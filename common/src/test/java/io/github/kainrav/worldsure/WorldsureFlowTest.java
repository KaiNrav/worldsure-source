package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldsureFlowTest {
    @TempDir
    Path game;

    private Path world() throws IOException {
        Path world = game.resolve("saves").resolve("My World");
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "level");
        Files.writeString(world.resolve("region").resolve("r.0.0.mca"), "chunks");
        Files.writeString(world.resolve("session.lock"), "lock");
        return world;
    }

    private List<Path> backups() throws IOException {
        Path dir = WorldBackup.backupsDir(game);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.sorted().toList();
        }
    }

    @Test
    void manifestRoundTrips() throws IOException {
        Path world = world();
        List<ModInfo> mods = List.of(new ModInfo("b", "2"), new ModInfo("a", "1"));
        new WorldManifest("fabric", "2026-10-15T20:00:00Z", mods).write(world);
        WorldManifest read = WorldManifest.read(world).orElseThrow();
        assertEquals(List.of(new ModInfo("a", "1"), new ModInfo("b", "2")), read.mods());
        assertEquals("fabric", read.loader());
    }

    @Test
    void newWorldGetsPassportWithoutBackup() throws IOException {
        Path world = world();
        List<ModInfo> mods = List.of(new ModInfo("minecraft", "26.3"));
        assertTrue(Worldsure.beforeWorldLoad(world, game, game.resolve("config"), mods).isEmpty());
        Worldsure.afterWorldLoad(world, "fabric", mods);
        assertTrue(Files.isRegularFile(WorldManifest.path(world)));
        assertTrue(backups().isEmpty());
    }

    @Test
    void removedModTriggersBackupAndReport() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", List.of(new ModInfo("create", "6.0.0"), new ModInfo("minecraft", "26.3")))
                .write(world);

        List<Report.Line> report = Worldsure.beforeWorldLoad(world, game, game.resolve("config"),
                List.of(new ModInfo("minecraft", "26.3")));

        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.REMOVED && line.text().contains("create")));
        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.BACKUP_OK));
        assertEquals(1, backups().size());
        try (ZipFile zip = new ZipFile(backups().get(0).toFile())) {
            assertNotNull(zip.getEntry("My World/level.dat"));
            assertNotNull(zip.getEntry("My World/region/r.0.0.mca"));
            assertNotNull(zip.getEntry("My World/worldsure/manifest.json"));
            assertNull(zip.getEntry("My World/session.lock"));
        }
        assertEquals(report, Worldsure.takeJoinReport());
        assertTrue(Worldsure.takeJoinReport().isEmpty(), "the report is shown once");
        assertTrue(Files.isRegularFile(game.resolve("config").resolve(WorldsureConfig.FILE_NAME)));
    }

    @Test
    void addedModOnlyDoesNotBackUp() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", List.of(new ModInfo("minecraft", "26.3"))).write(world);
        List<Report.Line> report = Worldsure.beforeWorldLoad(world, game, game.resolve("config"),
                List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "31")));
        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.ADDED));
        assertTrue(backups().isEmpty());
    }

    @Test
    void autoBackupCanBeTurnedOff() throws IOException {
        Path world = world();
        Path config = game.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve(WorldsureConfig.FILE_NAME), "autoBackup=false\n");
        new WorldManifest("fabric", "x", List.of(new ModInfo("create", "6"))).write(world);
        List<Report.Line> report = Worldsure.beforeWorldLoad(world, game, config, List.of());
        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.INFO));
        assertTrue(backups().isEmpty());
    }

    @Test
    void screenDecisionReplacesTheServerBackup() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", List.of(new ModInfo("create", "6"))).write(world);
        Worldsure.rememberDecision(world, Report.BackupResult.SKIPPED);
        List<Report.Line> report = Worldsure.beforeWorldLoad(world, game, game.resolve("config"), List.of());
        assertTrue(report.stream().anyMatch(line -> line.text().contains("without a backup")));
        assertTrue(backups().isEmpty());

        // The decision is used once; the next start backs up as usual.
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), List.of());
        assertEquals(1, backups().size());
    }

    @Test
    void countsAreSavedIntoThePassport() throws IOException {
        Path world = world();
        List<ModInfo> mods = List.of(new ModInfo("create", "6"));
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), mods);
        Worldsure.counts().putBlocks("minecraft:overworld", CountIndex.chunkKey(0, 0), java.util.Map.of("create", new long[]{7, 1}));
        Worldsure.onWorldClosed(world, "fabric", mods);

        assertEquals(new ObjectCounts(7, 1, 0), WorldManifest.read(world).orElseThrow().counts().get("create"));
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), mods);
        assertEquals(new ObjectCounts(7, 1, 0), Worldsure.counts().totals().get("create"));
    }

    @Test
    void keepsOnlyNewestBackups() throws IOException {
        Path world = world();
        List<Path> made = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            made.add(WorldBackup.create(world, game, 2, LocalDateTime.of(2026, 10, 15, 12, 0, i)));
        }
        assertEquals(made.subList(2, 4), backups());
    }
}
