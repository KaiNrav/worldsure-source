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
        index.putEntities("minecraft:overworld", CountIndex.chunkKey(0, 0), Map.of("create", 3L, "minecraft", 7L));

        assertEquals(Map.of("create", new ObjectCounts(15, 2, 3)), index.totals());
    }

    @Test
    void savingAChunkAgainReplacesItsCounts() {
        CountIndex index = new CountIndex();
        long chunk = CountIndex.chunkKey(-3, 7);
        index.putBlocks("minecraft:overworld", chunk, Map.of("create", new long[]{10, 1}));
        index.putEntities("minecraft:overworld", chunk, Map.of("create", 2L));
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
        index.putEntities("minecraft:overworld", CountIndex.chunkKey(1, -1), Map.of("alexsmobs", 4L));
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
    void reportDescribesCounts() {
        ModDiff diff = ModDiff.between(List.of(new ModInfo("create", "6"), new ModInfo("jei", "1")), List.of());
        List<Report.Line> lines = Report.changes(diff, Map.of("create", new ObjectCounts(1204, 3, 2)));
        assertEquals("  Removed: create 6 (1,204 blocks, 3 chests or machines, 2 mobs or other entities in the world)",
                lines.get(0).text());
        assertEquals("  Removed: jei 1 (nothing of it counted in saved chunks)", lines.get(1).text());
    }
}
