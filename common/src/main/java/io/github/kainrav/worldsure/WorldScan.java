package io.github.kainrav.worldsure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Counts the modded objects of a whole world once, in the background, so the counts are right even
 * for chunks nobody visited since Worldsure was installed. Each dimension's region and entity files
 * are read straight from disk; a dimension is marked scanned in the count index when it is done and
 * never read again. Chunks the game saves meanwhile are counted from the save, which always wins.
 */
public final class WorldScan {
    /** A dimension's id and its folders of chunk ({@code region}) and entity ({@code entities}) files. */
    public record Dimension(String id, Path regionFolder, Path entitiesFolder) {
    }

    /** Turns a stored chunk into counts; the game's NBT reading lives in the loader-independent Minecraft code. */
    public interface Counter {
        /** namespace -> {blocks, block entities, items}, or null when the chunk cannot be counted (an older format) */
        Map<String, long[]> blocks(InputStream nbt) throws IOException;

        /** namespace -> {entities, items}, or null when the chunk cannot be counted */
        Map<String, long[]> entities(InputStream nbt) throws IOException;
    }

    private final List<Dimension> dimensions;
    private final CountIndex index;
    private final Counter counter;
    private final RegionFiles.Decompressor decompressor;
    private volatile boolean stopped;
    private Thread thread;

    public WorldScan(List<Dimension> dimensions, CountIndex index, Counter counter, RegionFiles.Decompressor decompressor) {
        this.dimensions = List.copyOf(dimensions);
        this.index = index;
        this.counter = counter;
        this.decompressor = decompressor;
    }

    /** Whether some dimension still needs scanning. */
    public boolean isNeeded() {
        return dimensions.stream().anyMatch(dimension -> !index.isScanned(dimension.id()));
    }

    /** Starts scanning on a low-priority background thread. */
    public synchronized void start() {
        if (thread != null || !isNeeded()) {
            return;
        }
        thread = new Thread(this::run, "Worldsure scan");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    /** Stops the scan and waits a moment for it; a dimension not finished is scanned again next time. */
    public void stop() {
        stopped = true;
        Thread running;
        synchronized (this) {
            running = thread;
        }
        if (running != null) {
            try {
                running.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Scans every dimension not scanned yet, on the calling thread. */
    public void run() {
        for (Dimension dimension : dimensions) {
            if (stopped) {
                return;
            }
            if (index.isScanned(dimension.id())) {
                continue;
            }
            long start = System.nanoTime();
            try {
                int[] chunks = new int[2];
                scanFolder(dimension.regionFolder(), (x, z, nbt) -> {
                    Map<String, long[]> counts = counter.blocks(nbt);
                    if (counts != null) {
                        index.putScannedBlocks(dimension.id(), CountIndex.chunkKey(x, z), counts);
                        chunks[0]++;
                    }
                });
                scanFolder(dimension.entitiesFolder(), (x, z, nbt) -> {
                    Map<String, long[]> counts = counter.entities(nbt);
                    if (counts != null) {
                        index.putScannedEntities(dimension.id(), CountIndex.chunkKey(x, z), counts);
                        chunks[1]++;
                    }
                });
                if (stopped) {
                    return;
                }
                index.markScanned(dimension.id());
                Worldsure.LOG.info("Counted {} chunks and {} entity chunks of {} in {} ms", chunks[0], chunks[1],
                        dimension.id(), (System.nanoTime() - start) / 1_000_000);
            } catch (IOException | RuntimeException e) {
                Worldsure.LOG.warn("Could not count the objects of {}; trying again next time", dimension.id(), e);
            }
        }
    }

    private interface ChunkVisitor {
        void visit(int x, int z, InputStream nbt) throws IOException;
    }

    private void scanFolder(Path folder, ChunkVisitor visitor) throws IOException {
        for (Path region : RegionFiles.list(folder)) {
            if (stopped) {
                return;
            }
            for (RegionFiles.StoredChunk chunk : RegionFiles.read(region, decompressor)) {
                if (stopped) {
                    return;
                }
                try (InputStream nbt = chunk.nbt()) {
                    visitor.visit(chunk.x(), chunk.z(), nbt);
                } catch (IOException | RuntimeException e) {
                    // A damaged or half-written chunk; it is counted when the game saves it.
                }
            }
            // Leave the disk to the game between files.
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
