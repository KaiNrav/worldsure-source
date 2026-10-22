package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreTest {
    @TempDir
    Path game;

    private Path world(String name, String level) throws IOException {
        Path world = game.resolve(name);
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), level);
        Files.writeString(world.resolve("region").resolve("r.0.0.mca"), "chunks of " + level);
        return world;
    }

    @Test
    void listsBackupsNewestFirst() throws IOException {
        Path world = world("world", "v1");
        WorldBackup.create(world, game, 5, LocalDateTime.of(2026, 10, 1, 10, 0));
        WorldBackup.create(world, game, 5, LocalDateTime.of(2026, 10, 2, 10, 0));
        Files.writeString(WorldBackup.backupsDir(game).resolve("world_notes.zip"), "not a backup");
        List<WorldBackup.Backup> backups = WorldBackup.list(game, "world");
        assertEquals(2, backups.size());
        assertEquals(LocalDateTime.of(2026, 10, 2, 10, 0), backups.get(0).time());
        assertTrue(backups.get(0).size() > 0);
        assertTrue(WorldBackup.list(game, "other").isEmpty());
    }

    @Test
    void restoreKeepsTheReplacedWorld() throws IOException {
        Path world = world("world", "v1");
        Path backup = WorldBackup.create(world, game, 5, LocalDateTime.of(2026, 10, 1, 10, 0));
        Files.writeString(world.resolve("level.dat"), "v2");
        Files.writeString(world.resolve("new.txt"), "made after the backup");

        Path aside = WorldBackup.restore(backup, world, LocalDateTime.of(2026, 10, 3, 9, 0));

        assertEquals("v1", Files.readString(world.resolve("level.dat")));
        assertEquals("chunks of v1", Files.readString(world.resolve("region").resolve("r.0.0.mca")));
        assertFalse(Files.exists(world.resolve("new.txt")));
        assertEquals("world_before_restore_2026-10-03_09-00-00", aside.getFileName().toString());
        assertEquals("v2", Files.readString(aside.resolve("level.dat")));
        assertTrue(Files.exists(aside.resolve("new.txt")));
        assertFalse(Files.exists(game.resolve("world.restoring")));
    }

    @Test
    void aBrokenBackupLeavesTheWorldAlone() throws IOException {
        Path world = world("world", "v2");
        Path broken = WorldBackup.backupsDir(game).resolve("world_2026-10-01_10-00-00.zip");
        Files.createDirectories(broken.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(broken))) {
            zip.putNextEntry(new ZipEntry("world/region/r.0.0.mca"));
            zip.write(1);
        }
        assertThrows(IOException.class, () -> WorldBackup.restore(broken, world, LocalDateTime.now()));
        assertEquals("v2", Files.readString(world.resolve("level.dat")));
        assertFalse(Files.exists(game.resolve("world.restoring")));
        try (var files = Files.list(game)) {
            assertEquals(0, files.filter(p -> p.getFileName().toString().contains("before_restore")).count());
        }
    }

    @Test
    void refusesEntriesLeavingTheWorld() throws IOException {
        Path world = world("world", "v2");
        Path evil = WorldBackup.backupsDir(game).resolve("world_2026-10-01_10-00-00.zip");
        Files.createDirectories(evil.getParent());
        try (OutputStream out = Files.newOutputStream(evil); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("world/level.dat"));
            zip.write('x');
            zip.putNextEntry(new ZipEntry("world/../../escaped.txt"));
            zip.write('x');
        }
        assertThrows(IOException.class, () -> WorldBackup.restore(evil, world, LocalDateTime.now()));
        assertFalse(Files.exists(game.getParent().resolve("escaped.txt")));
        assertEquals("v2", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void aServerRestoresTheAskedBackupOnStart() throws IOException {
        Files.writeString(game.resolve("server.properties"), "motd=hi\nlevel-name=survival\n");
        Path world = world("survival", "v1");
        Path backup = WorldBackup.create(world, game, 5, LocalDateTime.of(2026, 10, 1, 10, 0));
        Files.writeString(world.resolve("level.dat"), "v2");

        assertTrue(Worldsure.restorePendingOnServer(game).isEmpty(), "nothing was asked");
        WorldBackup.requestRestore(game, "other", backup.getFileName().toString());
        assertTrue(Worldsure.restorePendingOnServer(game).isEmpty(), "the restore is for another world");
        assertEquals("v2", Files.readString(world.resolve("level.dat")));

        WorldBackup.requestRestore(game, "survival", backup.getFileName().toString());
        String result = Worldsure.restorePendingOnServer(game).orElseThrow();
        assertTrue(result.startsWith("Restored world survival"), result);
        assertEquals("v1", Files.readString(world.resolve("level.dat")));
        assertTrue(WorldBackup.pendingRestore(game).isEmpty(), "the request is done");
        assertTrue(Worldsure.restorePendingOnServer(game).isEmpty(), "it runs once");
    }

    @Test
    void aMissingBackupIsReportedAndForgotten() throws IOException {
        Path world = world("world", "v2");
        WorldBackup.requestRestore(game, "world", "world_2020-01-01_00-00-00.zip");
        String result = Worldsure.restorePending(world, game).orElseThrow();
        assertTrue(result.contains("is gone"), result);
        assertEquals("v2", Files.readString(world.resolve("level.dat")));
        assertTrue(WorldBackup.pendingRestore(game).isEmpty());
    }
}
