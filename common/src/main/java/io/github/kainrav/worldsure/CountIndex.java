package io.github.kainrav.worldsure;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Per-chunk counts of modded objects, refreshed whenever the game saves a chunk, so the totals
 * describe what is on disk. Vanilla ({@code minecraft}) objects are not counted: they never go missing.
 * Stored as {@code <world>/worldsure/index.dat}, which starts with the number of its format.
 *
 * <p>Formats: 1 held blocks and block entities per chunk and namespace, then entities; 2 (since 0.3.0)
 * adds the items found with the blocks (in containers) and with the entities (dropped, carried, framed).
 */
public final class CountIndex {
    public static final String FILE_NAME = "index.dat";
    /** The format this version writes. A layout change bumps it and teaches {@link #read} the old one. */
    public static final int FORMAT = 2;
    private static final String VANILLA = "minecraft";

    /** dimension -> chunk key -> namespace -> counts */
    private final Map<String, Map<Long, Chunk>> dimensions = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    /**
     * What one chunk holds; blocks and entities are saved at different times, so each half is replaced on its own.
     *
     * @param blocks   namespace -> {blocks, block entities, items in block entities}
     * @param entities namespace -> {entities, items with entities}
     */
    private record Chunk(Map<String, long[]> blocks, Map<String, long[]> entities) {
        Chunk withBlocks(Map<String, long[]> newBlocks) {
            return new Chunk(newBlocks, entities);
        }

        Chunk withEntities(Map<String, long[]> newEntities) {
            return new Chunk(blocks, newEntities);
        }

        boolean isEmpty() {
            return blocks.isEmpty() && entities.isEmpty();
        }
    }

    public static Path path(Path worldDir) {
        return worldDir.resolve(WorldManifest.DIRECTORY).resolve(FILE_NAME);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    /**
     * Replaces the block counts of one chunk.
     *
     * @param counts namespace -> {blocks, block entities} or {blocks, block entities, items}; vanilla entries are ignored
     */
    public void putBlocks(String dimension, long chunkKey, Map<String, long[]> counts) {
        update(dimension, chunkKey, chunk -> chunk.withBlocks(modded(counts, 3)));
    }

    /**
     * Replaces the entity counts of one chunk.
     *
     * @param counts namespace -> {entities} or {entities, items}; vanilla entries are ignored
     */
    public void putEntities(String dimension, long chunkKey, Map<String, long[]> counts) {
        update(dimension, chunkKey, chunk -> chunk.withEntities(modded(counts, 2)));
    }

    /** The non-vanilla, non-zero entries, each padded to {@code size} values. */
    private static Map<String, long[]> modded(Map<String, long[]> counts, int size) {
        Map<String, long[]> modded = new HashMap<>();
        counts.forEach((namespace, value) -> {
            long[] padded = new long[size];
            System.arraycopy(value, 0, padded, 0, Math.min(size, value.length));
            boolean any = false;
            for (long v : padded) {
                any |= v != 0;
            }
            if (!VANILLA.equals(namespace) && any) {
                modded.put(namespace, padded);
            }
        });
        return modded;
    }

    private void update(String dimension, long chunkKey, java.util.function.UnaryOperator<Chunk> change) {
        Map<Long, Chunk> chunks = dimensions.computeIfAbsent(dimension, d -> new ConcurrentHashMap<>());
        chunks.compute(chunkKey, (key, old) -> {
            Chunk updated = change.apply(old == null ? new Chunk(Map.of(), Map.of()) : old);
            return updated.isEmpty() ? null : updated;
        });
        dirty = true;
    }

    /** Totals per namespace across all dimensions, sorted by namespace. */
    public Map<String, ObjectCounts> totals() {
        Map<String, ObjectCounts> totals = new TreeMap<>();
        for (Map<Long, Chunk> chunks : dimensions.values()) {
            for (Chunk chunk : chunks.values()) {
                chunk.blocks().forEach((namespace, value) ->
                        totals.merge(namespace, new ObjectCounts(value[0], value[1], 0, value[2]), ObjectCounts::plus));
                chunk.entities().forEach((namespace, value) ->
                        totals.merge(namespace, new ObjectCounts(0, 0, value[0], value[1]), ObjectCounts::plus));
            }
        }
        return totals;
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Reads the counts of a world, empty if it has none yet. Older formats are read as they were
     * written; the next {@link #write} stores the current one.
     *
     * @throws NewerFormatException if a newer Worldsure wrote the counts
     */
    public static CountIndex read(Path worldDir) throws IOException {
        CountIndex index = new CountIndex();
        Path file = path(worldDir);
        if (!Files.isRegularFile(file)) {
            return index;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
            int format = in.readInt();
            if (format > FORMAT) {
                throw new NewerFormatException(file, format, FORMAT);
            }
            if (format < 1) {
                throw new IOException("Unknown count index format " + format);
            }
            boolean items = format >= 2;
            int dimensionCount = in.readInt();
            for (int d = 0; d < dimensionCount; d++) {
                String dimension = in.readUTF();
                Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
                int chunkCount = in.readInt();
                for (int c = 0; c < chunkCount; c++) {
                    long key = in.readLong();
                    Map<String, long[]> blocks = new HashMap<>();
                    int blockEntries = in.readInt();
                    for (int i = 0; i < blockEntries; i++) {
                        blocks.put(in.readUTF(), new long[]{in.readLong(), in.readLong(), items ? in.readLong() : 0});
                    }
                    Map<String, long[]> entities = new HashMap<>();
                    int entityEntries = in.readInt();
                    for (int i = 0; i < entityEntries; i++) {
                        entities.put(in.readUTF(), new long[]{in.readLong(), items ? in.readLong() : 0});
                    }
                    chunks.put(key, new Chunk(blocks, entities));
                }
                index.dimensions.put(dimension, chunks);
            }
        }
        return index;
    }

    public void write(Path worldDir) throws IOException {
        Path file = path(worldDir);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp))))) {
            out.writeInt(FORMAT);
            Map<String, Map<Long, Chunk>> snapshot = new TreeMap<>(dimensions);
            out.writeInt(snapshot.size());
            for (Map.Entry<String, Map<Long, Chunk>> dimension : snapshot.entrySet()) {
                out.writeUTF(dimension.getKey());
                Map<Long, Chunk> chunks = new HashMap<>(dimension.getValue());
                out.writeInt(chunks.size());
                for (Map.Entry<Long, Chunk> chunk : chunks.entrySet()) {
                    out.writeLong(chunk.getKey());
                    out.writeInt(chunk.getValue().blocks().size());
                    for (Map.Entry<String, long[]> entry : chunk.getValue().blocks().entrySet()) {
                        out.writeUTF(entry.getKey());
                        out.writeLong(entry.getValue()[0]);
                        out.writeLong(entry.getValue()[1]);
                        out.writeLong(entry.getValue()[2]);
                    }
                    out.writeInt(chunk.getValue().entities().size());
                    for (Map.Entry<String, long[]> entry : chunk.getValue().entities().entrySet()) {
                        out.writeUTF(entry.getKey());
                        out.writeLong(entry.getValue()[0]);
                        out.writeLong(entry.getValue()[1]);
                    }
                }
            }
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        dirty = false;
    }
}
