package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The formats are frozen (docs/formats.md): one file of each format, written by a separate script and kept
 * in src/test/resources/formats, must keep reading the same way in every version. A change to a format
 * needs a new number, an upgrade, and a new file here; it never changes what an old file means.
 */
class FormatFreezeTest {
    @TempDir
    Path game;

    private Path fixture(String name, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (InputStream in = FormatFreezeTest.class.getResourceAsStream("/formats/" + name)) {
            if (in == null) {
                throw new IOException("missing fixture " + name);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private Path world() throws IOException {
        Path world = game.resolve("world");
        Files.createDirectories(world);
        return world;
    }

    @Test
    void formatNumbersAreFrozen() {
        // Bumping one means: add a fixture of the new format, keep the old ones reading, update docs/formats.md.
        assertEquals(1, WorldManifest.FORMAT, "passport format");
        assertEquals(3, CountIndex.FORMAT, "count index format");
    }

    @Test
    void passportFormat1() throws IOException {
        Path world = world();
        fixture("manifest-format-1.json", WorldManifest.path(world));
        WorldManifest passport = WorldManifest.read(world).orElseThrow();
        assertEquals(1, passport.format());
        assertEquals("neoforge", passport.loader());
        assertEquals("2026-10-19T09:24:00+02:00", passport.savedAt());
        assertEquals(List.of(new ModInfo("create", "6.0.4"), new ModInfo("minecraft", "1.21.1"), new ModInfo("worldsure", "0.1.0")),
                passport.mods());
        assertEquals(Map.of("create", new ObjectCounts(120, 3, 2, 0)), passport.counts(), "a passport without items counts none");
    }

    @Test
    void passportWithoutFormat() throws IOException {
        Path world = world();
        fixture("manifest-without-format.json", WorldManifest.path(world));
        WorldManifest passport = WorldManifest.read(world).orElseThrow();
        assertEquals(1, passport.format());
        assertEquals(2, passport.mods().size());
        assertTrue(passport.counts().isEmpty());
    }

    @Test
    void countIndexFormats1To3() throws IOException {
        for (int format = 1; format <= 3; format++) {
            Path world = game.resolve("world" + format);
            fixture("index-format-" + format + ".dat", CountIndex.path(world));
            CountIndex index = CountIndex.read(world);
            long items = format >= 2 ? 1 : 0;
            assertEquals(Map.of(
                    "create", new ObjectCounts(120, 3, 0, format >= 2 ? 40 : 0),
                    "alexsmobs", new ObjectCounts(0, 0, 2, format >= 2 ? 5 : 0),
                    "mekanism", new ObjectCounts(7, 7, 0, items)), index.totals(), "format " + format);
            assertEquals(format >= 3, index.isScanned("minecraft:overworld"), "format " + format);
            assertFalse(index.isScanned("minecraft:the_nether"), "format " + format);

            // Written back in the current format, it reads the same.
            index.write(world);
            CountIndex again = CountIndex.read(world);
            assertEquals(index.totals(), again.totals(), "format " + format + " rewritten");
            assertEquals(format >= 3, again.isScanned("minecraft:overworld"), "format " + format + " rewritten");
        }
    }

    @Test
    void settingsFrom010() throws IOException {
        Path config = game.resolve("config");
        Path file = fixture("config-0.1.0.properties", config.resolve(WorldsureConfig.FILE_NAME));
        assertEquals(new WorldsureConfig(false, 3, false, false), WorldsureConfig.load(config));
        String text = Files.readString(file);
        assertTrue(text.contains("autoBackup=false") && text.contains("keepBackups=3"), "the old settings stay: " + text);
        assertTrue(text.contains("serverOnly=false") && text.contains("strict=false"), "new settings are added: " + text);
    }

    @Test
    void restoreRequestAndBackup() throws IOException {
        Path backups = WorldBackup.backupsDir(game);
        fixture("restore-pending.properties", backups.resolve(WorldBackup.PENDING_FILE));
        fixture("survival_2026-10-01_10-00-00.zip", backups.resolve("survival_2026-10-01_10-00-00.zip"));
        Path world = game.resolve("survival");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "level now");

        assertEquals(new WorldBackup.PendingRestore("survival", "survival_2026-10-01_10-00-00.zip"),
                WorldBackup.pendingRestore(game).orElseThrow());
        List<WorldBackup.Backup> listed = WorldBackup.list(game, "survival");
        assertEquals(1, listed.size());
        assertEquals(LocalDateTime.of(2026, 10, 1, 10, 0), listed.get(0).time());

        String result = Worldsure.restorePending(world, game).orElseThrow();
        assertTrue(result.startsWith("Restored world survival"), result);
        assertEquals("level from the backup", Files.readString(world.resolve("level.dat")));
        assertEquals("chunks from the backup", Files.readString(world.resolve("region").resolve("r.0.0.mca")));
    }
}
