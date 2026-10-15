package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDiffTest {
    @Test
    void sameModsMeanNoDiff() {
        List<ModInfo> mods = List.of(new ModInfo("create", "6.0.0"), new ModInfo("minecraft", "26.3"));
        ModDiff diff = ModDiff.between(mods, mods);
        assertTrue(diff.isEmpty());
        assertFalse(diff.isRisky());
    }

    @Test
    void detectsRemovedChangedAndAdded() {
        List<ModInfo> saved = List.of(
                new ModInfo("create", "6.0.0"),
                new ModInfo("jei", "31.0.0"),
                new ModInfo("sodium", "0.8.0"));
        List<ModInfo> now = List.of(
                new ModInfo("jei", "30.9.0"),
                new ModInfo("sodium", "0.8.1"),
                new ModInfo("worldsure", "0.1.0"));
        ModDiff diff = ModDiff.between(saved, now);

        assertEquals(List.of(new ModInfo("create", "6.0.0")), diff.removed());
        assertEquals(2, diff.changed().size());
        assertTrue(diff.changed().get(0).isDowngrade(), "jei went down");
        assertFalse(diff.changed().get(1).isDowngrade(), "sodium went up");
        assertEquals(List.of(new ModInfo("worldsure", "0.1.0")), diff.added());
        assertTrue(diff.isRisky());
        assertTrue(diff.hasDowngrade());
    }

    @Test
    void onlyAddingModsIsNotRisky() {
        ModDiff diff = ModDiff.between(
                List.of(new ModInfo("minecraft", "26.3")),
                List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "31.0.0")));
        assertFalse(diff.isEmpty());
        assertFalse(diff.isRisky());
    }
}
