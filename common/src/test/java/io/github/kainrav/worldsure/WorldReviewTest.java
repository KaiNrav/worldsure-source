package io.github.kainrav.worldsure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldReviewTest {
    @TempDir
    Path game;

    private static final List<ModInfo> SAVED = List.of(
            new ModInfo("minecraft", "26.3"),
            new ModInfo("create", "6.0.9"),
            new ModInfo("worldsure", "0.1.1"),
            new ModInfo("fabric-api", "0.160.12+26.3"));
    private static final List<ModInfo> INSTALLED = List.of(
            new ModInfo("minecraft", "26.3"),
            new ModInfo("worldsure", "0.1.0"),
            new ModInfo("fabric-api", "0.161.0+26.3"),
            new ModInfo("jei", "31.0"));
    private static final Map<String, ObjectCounts> COUNTS = Map.of(
            "create", new ObjectCounts(1240, 3, 2),
            "minecraft", new ObjectCounts(90_000, 12, 40));

    @AfterEach
    void closeWorld() {
        Worldsure.onWorldClosed(game.resolve("closed"), "fabric", INSTALLED);
    }

    private Path world() throws IOException {
        Path world = game.resolve("saves").resolve("My World");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "level");
        return world;
    }

    @Test
    void entriesAreSortedByDangerThenIdAndCarryCounts() {
        List<WorldReview.Entry> entries = WorldReview.entries(SAVED, INSTALLED, COUNTS);
        assertEquals(List.of(
                new WorldReview.Entry("create", WorldReview.Status.REMOVED, "6.0.9", "", new ObjectCounts(1240, 3, 2)),
                new WorldReview.Entry("worldsure", WorldReview.Status.DOWNGRADED, "0.1.1", "0.1.0", ObjectCounts.ZERO),
                new WorldReview.Entry("fabric-api", WorldReview.Status.UPDATED, "0.160.12+26.3", "0.161.0+26.3",
                        ObjectCounts.ZERO),
                new WorldReview.Entry("jei", WorldReview.Status.ADDED, "", "31.0", ObjectCounts.ZERO),
                new WorldReview.Entry("minecraft", WorldReview.Status.UNCHANGED, "26.3", "26.3",
                        new ObjectCounts(90_000, 12, 40))), entries);
    }

    @Test
    void reviewBeforeLoadingChangesNothing() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "2026-10-10T10:00:00+02:00", SAVED, COUNTS).write(world);

        WorldReview review = Worldsure.review(world, game.resolve("config"), INSTALLED).orElseThrow();

        assertEquals("My World", review.worldName());
        assertEquals("2026-10-10T10:00:00+02:00", review.savedAt());
        assertEquals(WorldReview.BackupState.WILL_BACKUP, review.backup().state());
        assertEquals(4, review.changes().size());
        assertEquals(new ObjectCounts(1240, 3, 2), review.changes().get(0).counts());
        assertFalse(Files.exists(WorldBackup.backupsDir(game)), "reviewing must not back up");
        assertTrue(Worldsure.currentReview().isEmpty());
    }

    @Test
    void reviewOfAWorldWithoutPassportIsEmpty() throws IOException {
        assertTrue(Worldsure.review(world(), game.resolve("config"), INSTALLED).isEmpty());
    }

    @Test
    void onlyAddedModsNeedNoBackup() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", List.of(new ModInfo("minecraft", "26.3"))).write(world);
        WorldReview review = Worldsure.review(world, game.resolve("config"),
                List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "31.0"))).orElseThrow();
        assertEquals(WorldReview.BackupState.NOT_NEEDED, review.backup().state());
        assertTrue(review.hasChanges());
    }

    @Test
    void disabledBackupIsShownBeforeLoading() throws IOException {
        Path world = world();
        Path config = game.resolve("config");
        Files.createDirectories(config);
        Files.writeString(config.resolve(WorldsureConfig.FILE_NAME), "autoBackup=false\n");
        new WorldManifest("fabric", "x", SAVED).write(world);
        assertEquals(WorldReview.BackupState.DISABLED,
                Worldsure.review(world, config, INSTALLED).orElseThrow().backup().state());
    }

    @Test
    void decisionOnTheScreenLeavesOnlyTheBackupInChat() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", SAVED).write(world);
        Report.BackupResult made = Worldsure.backup(world, game, game.resolve("config"));
        Worldsure.rememberDecision(world, made);

        List<Report.Line> logged = Worldsure.beforeWorldLoad(world, game, game.resolve("config"), INSTALLED);

        assertTrue(logged.size() > 2, "the log still gets the full report");
        List<Report.Line> chat = Worldsure.takeJoinReport();
        assertEquals(1, chat.size());
        assertEquals(Report.Kind.BACKUP_OK, chat.get(0).kind());
        WorldReview current = Worldsure.currentReview().orElseThrow();
        assertEquals(WorldReview.BackupState.CREATED, current.backup().state());
        assertTrue(current.backup().detail().endsWith(".zip"));
    }

    @Test
    void playingWithoutBackupIsRecorded() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", SAVED).write(world);
        Worldsure.rememberDecision(world, Report.BackupResult.SKIPPED);
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), INSTALLED);
        assertEquals(WorldReview.BackupState.SKIPPED, Worldsure.currentReview().orElseThrow().backup().state());
        assertEquals(Report.Kind.INFO, Worldsure.takeJoinReport().get(0).kind());
        assertFalse(Files.exists(WorldBackup.backupsDir(game)));
    }

    @Test
    void withoutTheScreenChatGetsTheFullReport() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", SAVED).write(world);
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), INSTALLED);
        assertTrue(Worldsure.takeJoinReport().size() > 2);
    }

    @Test
    void unchangedAndNewWorldsStillHaveAReview() throws IOException {
        Path world = world();
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), INSTALLED);
        assertEquals(WorldReview.BackupState.FIRST_VISIT, Worldsure.currentReview().orElseThrow().backup().state());

        Worldsure.afterWorldLoad(world, "fabric", INSTALLED);
        Worldsure.beforeWorldLoad(world, game, game.resolve("config"), INSTALLED);
        WorldReview review = Worldsure.currentReview().orElseThrow();
        assertFalse(review.hasChanges());
        assertEquals(WorldReview.BackupState.NOT_NEEDED, review.backup().state());
        assertEquals(INSTALLED.size(), review.entries().size());

        Worldsure.onWorldClosed(world, "fabric", INSTALLED);
        assertTrue(Worldsure.currentReview().isEmpty());
    }
}
