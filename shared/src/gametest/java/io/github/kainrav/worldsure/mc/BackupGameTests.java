package io.github.kainrav.worldsure.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kainrav.worldsure.WorldBackup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/** Game tests for /worldsure backups and /worldsure restore on the running server. */
public final class BackupGameTests {
    private BackupGameTests() {
    }

    /**
     * The commands are registered, list the world's backups and ask for a restore. The test server is
     * not a dedicated server, so the restore waits for the world to be opened again instead of stopping it.
     */
    public static void commandsListAndAskForRestore(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Path gameDir = Platform.get().gameDir();
        Path world = WorldsureMc.worldDir(server);
        String worldName = world.getFileName().toString();
        Path backup;
        try {
            backup = WorldBackup.create(world, gameDir, 5, LocalDateTime.of(2001, 2, 3, 4, 5, 6));
        } catch (IOException e) {
            helper.fail("could not back the test world up: " + e);
            return;
        }
        CommandSourceStack source = server.createCommandSourceStack();
        try {
            int listed = server.getCommands().getDispatcher().execute("worldsure backups", source);
            helper.assertTrue(listed >= 1, "/worldsure backups listed " + listed + " backups");
            int restored = server.getCommands().getDispatcher().execute("worldsure restore " + backup.getFileName(), source);
            helper.assertTrue(restored == 1, "/worldsure restore returned " + restored);
            int missing = server.getCommands().getDispatcher().execute("worldsure restore no_such_backup.zip", source);
            helper.assertTrue(missing == 0, "/worldsure restore of a missing backup returned " + missing);
        } catch (CommandSyntaxException e) {
            helper.fail("the command failed to parse: " + e.getMessage());
            return;
        }
        try {
            WorldBackup.PendingRestore pending = WorldBackup.pendingRestore(gameDir).orElse(null);
            helper.assertTrue(pending != null && pending.world().equals(worldName)
                    && pending.backup().equals(backup.getFileName().toString()), "the restore asked for is " + pending);
            List<WorldBackup.Backup> backups = WorldBackup.list(gameDir, worldName);
            helper.assertTrue(backups.stream().anyMatch(b -> b.file().equals(backup)), "the backup is not listed: " + backups);
            WorldBackup.clearPendingRestore(gameDir);
            Files.deleteIfExists(backup);
        } catch (IOException e) {
            helper.fail("could not read the restore asked for: " + e);
            return;
        }
        helper.succeed();
    }
}
