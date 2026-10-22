package io.github.kainrav.worldsure.mc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kainrav.worldsure.WorldBackup;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code /worldsure}: {@code backups} lists the automatic backups of the running world (game masters),
 * {@code restore <backup>} restores one (owners). A world cannot be swapped while it runs, so a dedicated
 * server stops and restores the backup when it starts again; in single player the backup is restored the
 * next time the world is opened.
 */
public final class WorldsureCommands {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private WorldsureCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldsure")
                .requires(gameMasters())
                .then(Commands.literal("backups").executes(context -> listBackups(context.getSource())))
                .then(Commands.literal("restore")
                        .requires(owners())
                        .then(Commands.argument("backup", StringArgumentType.greedyString())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        backups(context.getSource().getServer()).stream().map(WorldBackup.Backup::name), builder))
                                .executes(context -> restore(context.getSource(), StringArgumentType.getString(context, "backup"))))));
    }

    private static Predicate<CommandSourceStack> gameMasters() {
        //#if >=1.21.11
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //#else
        //$ return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //#endif
    }

    private static Predicate<CommandSourceStack> owners() {
        //#if >=1.21.11
        return Commands.hasPermission(Commands.LEVEL_OWNERS);
        //#else
        //$ return source -> source.hasPermission(Commands.LEVEL_OWNERS);
        //#endif
    }

    private static String worldName(MinecraftServer server) {
        return WorldsureMc.worldDir(server).getFileName().toString();
    }

    private static List<WorldBackup.Backup> backups(MinecraftServer server) {
        try {
            return WorldBackup.list(Platform.get().gameDir(), worldName(server));
        } catch (IOException e) {
            Worldsure.LOG.warn("Could not list the backups", e);
            return List.of();
        }
    }

    private static int listBackups(CommandSourceStack source) {
        List<WorldBackup.Backup> backups = backups(source.getServer());
        if (backups.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[Worldsure] World " + worldName(source.getServer())
                    + " has no automatic backups yet.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[Worldsure] Backups of " + worldName(source.getServer())
                + ", newest first:").withStyle(ChatFormatting.GOLD), false);
        for (WorldBackup.Backup backup : backups) {
            String line = "  " + TIME.format(backup.time()) + " · " + size(backup.size()) + " · " + backup.name();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        source.sendSuccess(() -> Component.literal("Restore one with /worldsure restore <file>.").withStyle(ChatFormatting.GRAY), false);
        return backups.size();
    }

    private static int restore(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        String trimmed = name.trim();
        Optional<WorldBackup.Backup> backup = backups(server).stream().filter(b -> b.name().equals(trimmed)).findFirst();
        if (backup.isEmpty()) {
            source.sendFailure(Component.literal("[Worldsure] No backup named " + trimmed + "; see /worldsure backups."));
            return 0;
        }
        try {
            WorldBackup.requestRestore(Platform.get().gameDir(), worldName(server), trimmed);
        } catch (IOException e) {
            source.sendFailure(Component.literal("[Worldsure] Could not ask for the restore: " + e.getMessage()));
            return 0;
        }
        Worldsure.LOG.warn("Restore of {} from {} asked for by {}", worldName(server), trimmed, source.getTextName());
        // Only a real dedicated server stops; a game test server also calls itself dedicated.
        if (server instanceof DedicatedServer) {
            source.sendSuccess(() -> Component.literal("[Worldsure] The server stops now and restores " + trimmed
                    + " when it starts again. The current world is kept next to it.").withStyle(ChatFormatting.GOLD), true);
            server.execute(() -> server.halt(false));
        } else {
            source.sendSuccess(() -> Component.literal("[Worldsure] Leave the world and open it again to restore " + trimmed
                    + ". The current world is kept as a separate copy.").withStyle(ChatFormatting.GOLD), true);
        }
        return 1;
    }

    /** A file size for people: 820 KB, 12.4 MB, 1.05 GB. */
    public static String size(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
