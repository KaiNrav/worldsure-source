package io.github.kainrav.worldsure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.LongAdder;

/**
 * How much time Worldsure's own work on saved data took in this session, so its cost can be seen
 * ({@code /worldsure status}, and the log when the world closes). Cheap enough to always run.
 */
public final class Timings {
    /** The kinds of work, in the order they are reported. */
    public enum Kind {
        CHUNK_READ("chunk reads"),
        CHUNK_WRITE("chunk writes"),
        ENTITY_READ("entity reads"),
        ENTITY_WRITE("entity writes"),
        ITEM_READ("item reads"),
        ITEM_WRITE("item writes");

        private final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private static final LongAdder[] NANOS = new LongAdder[Kind.values().length];
    private static final LongAdder[] CALLS = new LongAdder[Kind.values().length];

    static {
        for (int i = 0; i < NANOS.length; i++) {
            NANOS[i] = new LongAdder();
            CALLS[i] = new LongAdder();
        }
    }

    private Timings() {
    }

    /** Records one piece of work that started at {@code startNanos} (from {@link System#nanoTime()}). */
    public static void since(Kind kind, long startNanos) {
        NANOS[kind.ordinal()].add(System.nanoTime() - startNanos);
        CALLS[kind.ordinal()].increment();
    }

    public static long nanos(Kind kind) {
        return NANOS[kind.ordinal()].sum();
    }

    public static long calls(Kind kind) {
        return CALLS[kind.ordinal()].sum();
    }

    /** E.g. "chunk reads 12 ms over 340, chunk writes 30 ms over 512"; kinds that never ran are left out. */
    public static String summary() {
        List<String> parts = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            long calls = calls(kind);
            if (calls > 0) {
                parts.add(String.format(Locale.ROOT, "%s %,d ms over %,d", kind.label, nanos(kind) / 1_000_000, calls));
            }
        }
        return parts.isEmpty() ? "nothing yet" : String.join(", ", parts);
    }

    public static void reset() {
        for (int i = 0; i < NANOS.length; i++) {
            NANOS[i].reset();
            CALLS[i].reset();
        }
    }
}
