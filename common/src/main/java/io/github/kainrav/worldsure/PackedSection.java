package io.github.kainrav.worldsure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A chunk section's block states (or biomes) as stored on disk: a palette plus one index per position
 * packed into longs, never split across two longs. Block states have 4096 positions at least 4 bits
 * each; biomes 64 positions at least 1 bit. Palette entries are opaque (NBT tags in the game), so this
 * works without Minecraft classes.
 */
public record PackedSection<E>(List<E> palette, int[] indices) {
    public static final int SIZE = 4096;
    public static final int BIOME_SIZE = 64;
    private static final int MIN_BITS = 4;

    /** A section's block states; {@code globalBits} see {@link #pack(int)}. */
    public static <E> PackedSection<E> unpack(List<E> palette, long[] data, int globalBits) {
        return unpack(palette, data, SIZE, MIN_BITS, globalBits);
    }

    /** A section's 4x4x4 biomes. */
    public static <E> PackedSection<E> unpackBiomes(List<E> palette, long[] data) {
        return unpack(palette, data, BIOME_SIZE, 1, 0);
    }

    private static <E> PackedSection<E> unpack(List<E> palette, long[] data, int size, int minBits, int globalBits) {
        int[] indices = new int[size];
        if (palette.size() > 1 && data != null && data.length > 0) {
            int bits = bitsFor(palette.size(), minBits, globalBits);
            int perLong = 64 / bits;
            if (data.length != (size + perLong - 1) / perLong) {
                throw new IllegalArgumentException("Expected " + (size + perLong - 1) / perLong + " longs at "
                        + bits + " bits for a palette of " + palette.size() + ", got " + data.length);
            }
            long mask = (1L << bits) - 1;
            for (int i = 0; i < size; i++) {
                int index = (int) ((data[i / perLong] >>> ((i % perLong) * bits)) & mask);
                if (index >= palette.size()) {
                    throw new IllegalArgumentException("Palette index " + index + " out of range " + palette.size());
                }
                indices[i] = index;
            }
        }
        return new PackedSection<>(List.copyOf(palette), indices);
    }

    /** At least {@code minBits}, exact bits up to 256 entries, the global width beyond. */
    private static int bitsFor(int paletteSize, int minBits, int globalBits) {
        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        return bits > 8 ? Math.max(bits, globalBits) : bits;
    }

    public int size() {
        return indices.length;
    }

    /**
     * Packs the indices the way the game does; {@code null} when the palette has one entry and no data is stored.
     *
     * @param globalBits bits per entry the game uses once a palette outgrows 256 entries
     *                   (enough for every block state id)
     */
    public long[] pack(int globalBits) {
        return pack(MIN_BITS, globalBits);
    }

    /** Packs biomes the way the game does; {@code null} when there is one biome. */
    public long[] packBiomes() {
        return pack(1, 0);
    }

    private long[] pack(int minBits, int globalBits) {
        if (palette.size() <= 1) {
            return null;
        }
        int bits = bitsFor(palette.size(), minBits, globalBits);
        int perLong = 64 / bits;
        int size = size();
        long[] data = new long[(size + perLong - 1) / perLong];
        for (int i = 0; i < size; i++) {
            data[i / perLong] |= ((long) indices[i]) << ((i % perLong) * bits);
        }
        return data;
    }

    /** How many positions hold each key, e.g. blocks per mod namespace. */
    public <K> Map<K, Long> countBy(java.util.function.Function<E, K> key) {
        if (palette.isEmpty()) {
            return Map.of();
        }
        long[] perEntry = new long[palette.size()];
        for (int index : indices) {
            perEntry[index]++;
        }
        Map<K, Long> counts = new LinkedHashMap<>();
        for (int i = 0; i < perEntry.length; i++) {
            if (perEntry[i] > 0) {
                counts.merge(key.apply(palette.get(i)), perEntry[i], Long::sum);
            }
        }
        return counts;
    }

    public E get(int position) {
        return palette.get(indices[position]);
    }

    public boolean contains(Predicate<E> test) {
        boolean[] used = used();
        for (int i = 0; i < palette.size(); i++) {
            if (used[i] && test.test(palette.get(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean[] used() {
        boolean[] used = new boolean[palette.size()];
        for (int index : indices) {
            used[index] = true;
        }
        return used;
    }

    /** Builds a section from one entry per position, with a palette of the distinct entries in first-seen order. */
    public static <E> PackedSection<E> of(List<E> entries) {
        Map<E, Integer> ids = new LinkedHashMap<>();
        int[] indices = new int[entries.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = ids.computeIfAbsent(entries.get(i), e -> ids.size());
        }
        return new PackedSection<>(new ArrayList<>(ids.keySet()), indices);
    }

    /** Replaces every entry matching {@code test} with {@code replacement}, dropping unused palette entries. */
    public PackedSection<E> replace(Predicate<E> test, E replacement) {
        List<E> entries = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            E entry = get(i);
            entries.add(test.test(entry) ? replacement : entry);
        }
        return of(entries);
    }

    /**
     * Puts the original entries back where this section still holds a placeholder.
     *
     * @param original the section as it was on disk before the placeholders went in
     */
    public PackedSection<E> restore(Predicate<E> isPlaceholder, PackedSection<E> original) {
        List<E> entries = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            E entry = get(i);
            entries.add(isPlaceholder.test(entry) ? original.get(i) : entry);
        }
        return of(entries);
    }

    /** Puts the original entries back at the given positions. */
    public PackedSection<E> restoreAt(boolean[] positions, PackedSection<E> original) {
        List<E> entries = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            entries.add(positions[i] ? original.get(i) : get(i));
        }
        return of(entries);
    }

    /**
     * Puts the original entries back wherever this section still holds what it was given at load,
     * and keeps every position that changed since.
     *
     * @param loaded   the section as the game got it, with stand-ins for the original entries
     * @param original the section as it was on disk
     */
    public PackedSection<E> restoreUnchanged(PackedSection<E> loaded, PackedSection<E> original) {
        List<E> entries = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            E entry = get(i);
            entries.add(entry.equals(loaded.get(i)) ? original.get(i) : entry);
        }
        return of(entries);
    }
}
