package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackedSectionTest {
    private static List<String> layered(String... layers) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < PackedSection.SIZE; i++) {
            entries.add(layers[(i / 256) % layers.length]);
        }
        return entries;
    }

    @Test
    void packsAndUnpacksAtEveryWidth() {
        for (int size : new int[]{2, 16, 17, 40, 300}) {
            List<String> entries = new ArrayList<>();
            for (int i = 0; i < PackedSection.SIZE; i++) {
                entries.add("b" + (i * 7 % size));
            }
            PackedSection<String> section = PackedSection.of(entries);
            assertEquals(size, section.palette().size());
            PackedSection<String> back = PackedSection.unpack(section.palette(), section.pack(15), 15);
            assertArrayEquals(section.indices(), back.indices(), "palette of " + size);
        }
    }

    @Test
    void knownVanillaLayout() {
        // 5 entries need 4 bits: 16 per long, 256 longs, index i sits at bit (i % 16) * 4.
        List<String> entries = layered("a", "b", "c", "d", "e");
        long[] data = PackedSection.of(entries).pack(15);
        assertEquals(256, data.length);
        assertEquals(0L, data[0]);
        assertEquals(0x1111111111111111L, data[16]);
    }

    @Test
    void largePalettesUseTheGlobalWidth() {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < PackedSection.SIZE; i++) {
            entries.add("b" + (i % 300));
        }
        PackedSection<String> section = PackedSection.of(entries);
        long[] data = section.pack(15);
        assertEquals(1024, data.length, "15 bits: 4 per long");
        assertArrayEquals(section.indices(), PackedSection.unpack(section.palette(), data, 15).indices());
    }

    @Test
    void countsByKey() {
        PackedSection<String> section = PackedSection.of(layered("minecraft:stone", "create:press", "create:shaft", "minecraft:air"));
        assertEquals(java.util.Map.of("minecraft", 2048L, "create", 2048L),
                section.countBy(name -> name.substring(0, name.indexOf(':'))));
    }

    @Test
    void singleEntryHasNoData() {
        PackedSection<String> section = PackedSection.of(layered("air"));
        assertNull(section.pack(15));
        assertEquals("air", PackedSection.unpack(List.of("air"), null, 15).get(4095));
    }

    @Test
    void placeholdersRoundTrip() {
        List<String> original = layered("stone", "create:press", "dirt", "create:shaft");
        PackedSection<String> disk = PackedSection.of(original);
        PackedSection<String> loaded = disk.replace(e -> e.startsWith("create:"), "worldsure:placeholder");
        assertEquals(List.of("stone", "worldsure:placeholder", "dirt"), loaded.palette());

        // The player edits a normal block while the placeholders stay put.
        List<String> edited = new ArrayList<>();
        for (int i = 0; i < PackedSection.SIZE; i++) {
            edited.add(i == 512 ? "glass" : loaded.get(i));
        }
        PackedSection<String> saved = PackedSection.of(edited).restore("worldsure:placeholder"::equals, disk);
        for (int i = 0; i < PackedSection.SIZE; i++) {
            assertEquals(i == 512 ? "glass" : original.get(i), saved.get(i));
        }
        assertFalse(saved.contains("worldsure:placeholder"::equals));
        assertTrue(saved.contains("create:press"::equals));
    }
}
