package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldScanTest {
    @TempDir
    Path world;

    /** A region file holding the given payloads at local chunk indices, compressed with zlib or gzip. */
    static void writeRegion(Path file, Map<Integer, String> payloads, boolean gzip) throws IOException {
        Files.createDirectories(file.getParent());
        try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
            out.setLength(8192);
            int sector = 2;
            for (Map.Entry<Integer, String> entry : payloads.entrySet()) {
                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                try (var stream = gzip ? new GZIPOutputStream(compressed) : new DeflaterOutputStream(compressed)) {
                    stream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                byte[] data = compressed.toByteArray();
                int sectors = (data.length + 5 + 4095) / 4096;
                out.seek(entry.getKey() * 4L);
                out.writeInt((sector << 8) | sectors);
                out.seek(sector * 4096L);
                out.writeInt(data.length + 1);
                out.writeByte(gzip ? 1 : 2);
                out.write(data);
                sector += sectors;
            }
            out.setLength(Math.max(out.length(), sector * 4096L));
        }
    }

    /** Payloads are "namespace=count;..."; blocks get {count, 0, 0}, entities {count, 0}. */
    private static final WorldScan.Counter COUNTER = new WorldScan.Counter() {
        @Override
        public Map<String, long[]> blocks(InputStream nbt) throws IOException {
            return parse(nbt, 3);
        }

        @Override
        public Map<String, long[]> entities(InputStream nbt) throws IOException {
            return parse(nbt, 2);
        }

        private Map<String, long[]> parse(InputStream nbt, int size) throws IOException {
            String text = new String(nbt.readAllBytes(), StandardCharsets.UTF_8);
            if (text.equals("old")) {
                return null;
            }
            Map<String, long[]> counts = new HashMap<>();
            for (String part : text.split(";")) {
                String[] kv = part.split("=");
                long[] value = new long[size];
                value[0] = Long.parseLong(kv[1]);
                counts.put(kv[0], value);
            }
            return counts;
        }
    };

    private static final RegionFiles.Decompressor NO_OTHER = (method, in) -> null;

    @Test
    void readsEveryStoredChunkWithItsPosition() throws IOException {
        Path region = world.resolve("region").resolve("r.-1.2.mca");
        writeRegion(region, Map.of(0, "a=1", 33, "b=2"), false);
        List<RegionFiles.StoredChunk> chunks = RegionFiles.read(region, NO_OTHER);
        assertEquals(2, chunks.size());
        Map<String, String> byPos = new HashMap<>();
        for (RegionFiles.StoredChunk chunk : chunks) {
            byPos.put(chunk.x() + "," + chunk.z(), new String(chunk.nbt().readAllBytes(), StandardCharsets.UTF_8));
        }
        // Index 0 is chunk (-32, 64); index 33 is one further in x and z.
        assertEquals(Map.of("-32,64", "a=1", "-31,65", "b=2"), byPos);
    }

    @Test
    void countsTheWholeWorldOnce() throws IOException {
        writeRegion(world.resolve("region").resolve("r.0.0.mca"), Map.of(0, "create=10", 1, "old", 2, "create=5;mekanism=1"), false);
        writeRegion(world.resolve("entities").resolve("r.0.0.mca"), Map.of(0, "alexsmobs=3"), true);
        CountIndex index = new CountIndex();
        WorldScan.Dimension overworld = new WorldScan.Dimension("minecraft:overworld", world.resolve("region"), world.resolve("entities"));
        WorldScan.Dimension nether = new WorldScan.Dimension("minecraft:the_nether", world.resolve("DIM-1/region"), world.resolve("DIM-1/entities"));

        WorldScan scan = new WorldScan(List.of(overworld, nether), index, COUNTER, NO_OTHER);
        assertTrue(scan.isNeeded());
        scan.run();
        assertEquals(Map.of("create", new ObjectCounts(15, 0, 0), "mekanism", new ObjectCounts(1, 0, 0),
                "alexsmobs", new ObjectCounts(0, 0, 3)), index.totals());
        assertTrue(index.isScanned("minecraft:overworld"));
        assertTrue(index.isScanned("minecraft:the_nether"), "a dimension without files is done at once");

        AtomicInteger reads = new AtomicInteger();
        WorldScan again = new WorldScan(List.of(overworld), index, new WorldScan.Counter() {
            @Override
            public Map<String, long[]> blocks(InputStream nbt) {
                reads.incrementAndGet();
                return Map.of();
            }

            @Override
            public Map<String, long[]> entities(InputStream nbt) {
                reads.incrementAndGet();
                return Map.of();
            }
        }, NO_OTHER);
        assertFalse(again.isNeeded());
        again.run();
        assertEquals(0, reads.get(), "a scanned dimension is not read again");

        // The scan marks survive a restart.
        index.write(world);
        assertTrue(CountIndex.read(world).isScanned("minecraft:overworld"));
    }

    @Test
    void aSaveDuringTheScanWins() throws IOException {
        writeRegion(world.resolve("region").resolve("r.0.0.mca"), Map.of(0, "create=10"), false);
        CountIndex index = new CountIndex();
        // The player removed the machines in chunk 0,0 and the game saved it before the scan got there.
        index.putBlocks("minecraft:overworld", CountIndex.chunkKey(0, 0), Map.of("create", new long[]{2, 0}));
        new WorldScan(List.of(new WorldScan.Dimension("minecraft:overworld", world.resolve("region"), world.resolve("entities"))),
                index, COUNTER, NO_OTHER).run();
        assertEquals(new ObjectCounts(2, 0, 0), index.totals().get("create"));
    }

    @Test
    void skipsHalfWrittenChunks() throws IOException {
        Path region = world.resolve("region").resolve("r.0.0.mca");
        writeRegion(region, Map.of(0, "create=10", 1, "create=1"), false);
        try (RandomAccessFile out = new RandomAccessFile(region.toFile(), "rw")) {
            // Chunk 1 claims a length larger than its sectors, as while the game rewrites it.
            out.seek(4);
            int entry = out.readInt();
            out.seek((entry >>> 8) * 4096L);
            out.writeInt(1_000_000);
        }
        CountIndex index = new CountIndex();
        new WorldScan(List.of(new WorldScan.Dimension("minecraft:overworld", region.getParent(), world.resolve("entities"))),
                index, COUNTER, NO_OTHER).run();
        assertEquals(new ObjectCounts(10, 0, 0), index.totals().get("create"));
    }

    @Test
    void readsTheSecondIndexFormat() throws IOException {
        Path file = CountIndex.path(world);
        Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(2);
            out.writeInt(1);
            out.writeUTF("minecraft:overworld");
            out.writeInt(1);
            out.writeLong(CountIndex.chunkKey(0, 0));
            out.writeInt(1);
            out.writeUTF("create");
            out.writeLong(4);
            out.writeLong(1);
            out.writeLong(9);
            out.writeInt(0);
        }
        CountIndex index = CountIndex.read(world);
        assertEquals(Map.of("create", new ObjectCounts(4, 1, 0, 9)), index.totals());
        assertFalse(index.isScanned("minecraft:overworld"), "a world counted before 0.4.0 is scanned once");
    }
}
