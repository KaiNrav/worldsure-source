package io.github.kainrav.worldsure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A 16x16x16 chunk section's block states as stored on disk: a palette plus 4096 indices packed
 * into longs, at least 4 bits each and never split across two longs. Palette entries are opaque
 * (NBT compounds in the game), so this works without Minecraft classes.
 */
public record PackedSection<E>(List<E> palette, int[] indices) {
    public static final int SIZE = 4096;
    private static final int MIN_BITS = 4;

    /** @param globalBits see {@link #pack(int)} */
    public static <E> PackedSection<E> unpack(List<E> palette, long[] data, int globalBits) {
        int[] indices = new int[SIZE];
        if (palette.size() > 1 && data != null && data.length > 0) {
            int bits = bitsFor(palette.size(), globalBits);
            int perLong = 64 / bits;
            if (data.length != (SIZE + perLong - 1) / perLong) {
                throw new IllegalArgumentException("Expected " + (SIZE + perLong - 1) / perLong + " longs at "
                        + bits + " bits for a palette of " + palette.size() + ", got " + data.length);
            }
            long mask = (1L << bits) - 1;
            for (int i = 0; i < SIZE; i++) {
                int index = (int) ((data[i / perLong] >>> ((i % perLong) * bits)) & mask);
                if (index >= palette.size()) {
                    throw new IllegalArgumentException("Palette index " + index + " out of range " + palette.size());
                }
                indices[i] = index;
            }
        }
        return new PackedSection<>(List.copyOf(palette), indices);
    }

    /** At least 4 bits, exact bits up to 256 entries, the global width beyond. */
    private static int bitsFor(int paletteSize, int globalBits) {
        int bits = Math.max(MIN_BITS, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        return bits > 8 ? Math.max(bits, globalBits) : bits;
    }

    /**
     * Packs the indices the way the game does; {@code null} when the palette has one entry and no data is stored.
     *
     * @param globalBits bits per entry the game uses once a palette outgrows 256 entries
     *                   (enough for every block state id)
     */
    public long[] pack(int globalBits) {
        if (palette.size() <= 1) {
            return null;
        }
        int bits = bitsFor(palette.size(), globalBits);
        int perLong = 64 / bits;
        long[] data = new long[(SIZE + perLong - 1) / perLong];
        for (int i = 0; i < SIZE; i++) {
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
        int[] indices = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            indices[i] = ids.computeIfAbsent(entries.get(i), e -> ids.size());
        }
        return new PackedSection<>(new ArrayList<>(ids.keySet()), indices);
    }

    /** Replaces every entry matching {@code test} with {@code replacement}, dropping unused palette entries. */
    public PackedSection<E> replace(Predicate<E> test, E replacement) {
        List<E> entries = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
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
        List<E> entries = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            E entry = get(i);
            entries.add(isPlaceholder.test(entry) ? original.get(i) : entry);
        }
        return of(entries);
    }
}
