package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountIndexTest {
    @TempDir
    Path world;

    @Test
    void sumsChunksAndSkipsVanilla() {
        CountIndex index = new CountIndex();
        index.putBlocks("minecraft:overworld", CountIndex.chunkKey(0, 0),
                Map.of("create", new long[]{10, 2}, "minecraft", new long[]{4000, 5}));
        index.putBlocks("minecraft:the_nether", CountIndex.chunkKey(0, 0), Map.of("create", new long[]{5, 0}));
        index.putEntities("minecraft:overworld", CountIndex.chunkKey(0, 0), Map.of("create", new long[]{3}, "minecraft", new long[]{7}));

        assertEquals(Map.of("create", new ObjectCounts(15, 2, 3)), index.totals());
    }

    @Test
    void savingAChunkAgainReplacesItsCounts() {
        CountIndex index = new CountIndex();
        long chunk = CountIndex.chunkKey(-3, 7);
        index.putBlocks("minecraft:overworld", chunk, Map.of("create", new long[]{10, 1}));
        index.putEntities("minecraft:overworld", chunk, Map.of("create", new long[]{2}));
        index.putBlocks("minecraft:overworld", chunk, Map.of("create", new long[]{4, 0}));
        assertEquals(new ObjectCounts(4, 0, 2), index.totals().get("create"));

        index.putBlocks("minecraft:overworld", chunk, Map.of());
        index.putEntities("minecraft:overworld", chunk, Map.of());
        assertTrue(index.totals().isEmpty());
    }

    @Test
    void roundTripsThroughDisk() throws IOException {
        CountIndex index = new CountIndex();
        index.putBlocks("minecraft:overworld", CountIndex.chunkKey(1, -1), Map.of("create", new long[]{10, 2}));
        index.putEntities("minecraft:overworld", CountIndex.chunkKey(1, -1), Map.of("alexsmobs", new long[]{4}));
        assertTrue(index.isDirty());
        index.write(world);
        assertFalse(index.isDirty());

        CountIndex read = CountIndex.read(world);
        assertEquals(index.totals(), read.totals());
        // Replacing one half keeps the other half of the read chunk.
        read.putBlocks("minecraft:overworld", CountIndex.chunkKey(1, -1), Map.of());
        assertEquals(Map.of("alexsmobs", new ObjectCounts(0, 0, 4)), read.totals());
    }

    @Test
    void itemsAreCountedWithBlocksAndEntities() throws IOException {
        CountIndex index = new CountIndex();
        long chunk = CountIndex.chunkKey(2, 2);
        index.putBlocks("minecraft:overworld", chunk, Map.of("create", new long[]{10, 1, 64}, "minecraft", new long[]{1, 1, 5}));
        index.putEntities("minecraft:overworld", chunk, Map.of("create", new long[]{0, 3}));
        assertEquals(Map.of("create", new ObjectCounts(10, 1, 0, 67)), index.totals());

        index.write(world);
        assertEquals(index.totals(), CountIndex.read(world).totals());
    }

    @Test
    void readsTheFirstFormat() throws IOException {
        Path file = CountIndex.path(world);
        java.nio.file.Files.createDirectories(file.getParent());
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(
                new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(file)))) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("minecraft:overworld");
            out.writeInt(1);
            out.writeLong(CountIndex.chunkKey(0, 0));
            out.writeInt(1);
            out.writeUTF("create");
            out.writeLong(12);
            out.writeLong(2);
            out.writeInt(1);
            out.writeUTF("create");
            out.writeLong(5);
        }
        CountIndex index = CountIndex.read(world);
        assertEquals(Map.of("create", new ObjectCounts(12, 2, 5, 0)), index.totals());

        index.write(world);
        try (java.io.DataInputStream in = new java.io.DataInputStream(
                new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(file)))) {
            assertEquals(CountIndex.FORMAT, in.readInt(), "rewritten in the current format");
        }
        assertEquals(index.totals(), CountIndex.read(world).totals());
    }

    @Test
    void reportDescribesCounts() {
        ModDiff diff = ModDiff.between(List.of(new ModInfo("create", "6"), new ModInfo("jei", "1")), List.of());
        List<Report.Line> lines = Report.changes(diff, Map.of("create", new ObjectCounts(1204, 3, 2)));
        assertEquals("  Removed: create 6 (1,204 blocks, 3 chests or machines, 2 mobs or other entities in the world)",
                lines.get(0).text());
        assertEquals("  Removed: jei 1 (nothing of it counted in saved chunks)", lines.get(1).text());
    }
}
