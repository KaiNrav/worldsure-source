package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    private static int cmp(String a, String b) {
        return Integer.signum(VersionComparator.INSTANCE.compare(a, b));
    }

    @Test
    void numericPartsCompareAsNumbers() {
        assertEquals(-1, cmp("1.9.0", "1.10.0"));
        assertEquals(1, cmp("2.0", "1.99.99"));
        assertEquals(0, cmp("1.0", "1.0.0"));
    }

    @Test
    void preReleaseSortsBeforeRelease() {
        assertEquals(-1, cmp("1.2.0-beta.1", "1.2.0"));
        assertEquals(-1, cmp("1.2.0-alpha", "1.2.0-beta"));
    }

    @Test
    void buildMetadataIsIgnored() {
        assertEquals(0, cmp("0.161.0+26.3", "0.161.0+26.2"));
        assertTrue(cmp("0.160.0+26.3", "0.161.0+26.3") < 0);
    }
}
