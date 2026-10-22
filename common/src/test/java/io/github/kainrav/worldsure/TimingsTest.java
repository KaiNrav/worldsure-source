package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingsTest {
    @Test
    void summarizesOnlyWhatRan() {
        Timings.reset();
        assertEquals("nothing yet", Timings.summary());
        long start = System.nanoTime() - 3_000_000;
        Timings.since(Timings.Kind.CHUNK_WRITE, start);
        Timings.since(Timings.Kind.CHUNK_WRITE, System.nanoTime());
        assertEquals(2, Timings.calls(Timings.Kind.CHUNK_WRITE));
        String summary = Timings.summary();
        assertTrue(summary.startsWith("chunk writes ") && summary.endsWith(" over 2"), summary);
        assertTrue(Timings.nanos(Timings.Kind.CHUNK_WRITE) >= 3_000_000, summary);
        Timings.reset();
        assertEquals(0, Timings.calls(Timings.Kind.CHUNK_WRITE));
    }
}
