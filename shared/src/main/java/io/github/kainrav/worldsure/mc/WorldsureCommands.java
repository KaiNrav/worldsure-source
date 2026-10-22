package io.github.kainrav.worldsure.mc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.kainrav.worldsure.ObjectCounts;
import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.WorldBackup;
import io.github.kainrav.worldsure.WorldReview;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.WorldsureConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * {@code /worldsure}, for game masters: {@code status} tells what changed at start, what the world holds
 * per mod and the settings; {@code backups} lists the automatic backups; {@code backup} saves and backs
 * the world up now; {@code scan} counts the whole world again. For owners, {@code restore <backup>}
 * restores one: a world cannot be swapped while it runs, so a dedicated server stops and restores the
 * backup when it starts again, and in single player it is restored the next time the world is opened.
 */
public final class WorldsureCommands {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private WorldsureCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldsure")
                .requires(gameMasters())
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("backups").executes(context -> listBackups(context.getSource())))
                .then(Commands.literal("backup").executes(context -> backupNow(context.getSource())))
                .then(Commands.literal("scan").executes(context -> scan(context.getSource())))
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

    private static void say(CommandSourceStack source, String text, ChatFormatting style) {
        source.sendSuccess(() -> Component.literal(text).withStyle(style), false);
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Path world = WorldsureMc.worldDir(server);
        WorldsureConfig config = Worldsure.config(Platform.get().configDir());
        say(source, "[Worldsure] World " + worldName(server) + " · " + (Placeholders.serverOnly() ? "server-only placeholders" : "Worldsure placeholders")
                + " · strict " + (config.strict() ? "on" : "off") + " · automatic backups " + (config.autoBackup() ? "on, " + config.keepBackups() + " kept" : "off"),
                ChatFormatting.GOLD);

        Optional<WorldReview> review = Worldsure.currentReview();
        if (review.isPresent() && review.get().hasChanges()) {
            Map<WorldReview.Status, Long> byStatus = new EnumMap<>(WorldReview.Status.class);
            review.get().changes().forEach(entry -> byStatus.merge(entry.status(), 1L, Long::sum));
            say(source, "  Mod changes when the world started: " + byStatus.getOrDefault(WorldReview.Status.REMOVED, 0L) + " removed, "
                    + byStatus.getOrDefault(WorldReview.Status.DOWNGRADED, 0L) + " downgraded, "
                    + byStatus.getOrDefault(WorldReview.Status.UPDATED, 0L) + " updated, "
                    + byStatus.getOrDefault(WorldReview.Status.ADDED, 0L) + " added", ChatFormatting.YELLOW);
        } else {
            say(source, "  No mod changes when the world started.", ChatFormatting.GRAY);
        }

        Map<String, ObjectCounts> totals = Worldsure.counts().totals();
        String counting = Worldsure.isScanning() ? " (still counting the whole world)" : "";
        if (totals.isEmpty()) {
            say(source, "  No modded objects counted" + counting + ".", ChatFormatting.GRAY);
        } else {
            say(source, "  Modded objects in the world" + counting + ":", ChatFormatting.GRAY);
            totals.entrySet().stream()
                    .sorted(Comparator.comparingLong((Map.Entry<String, ObjectCounts> e) -> -total(e.getValue())))
                    .limit(8)
                    .forEach(e -> say(source, "    " + e.getKey() + ": " + describe(e.getValue()), ChatFormatting.WHITE));
            if (totals.size() > 8) {
                say(source, "    and " + (totals.size() - 8) + " more mods", ChatFormatting.GRAY);
            }
        }
        List<WorldBackup.Backup> backups = backups(server);
        say(source, "  Backups: " + backups.size() + (backups.isEmpty() ? "" : ", newest " + TIME.format(backups.get(0).time()))
                + " (" + WorldBackup.backupsDir(Platform.get().gameDir()) + ")", ChatFormatting.GRAY);
        if (!Files.isDirectory(world)) {
            say(source, "  The world folder " + world + " is missing.", ChatFormatting.RED);
        }
        return totals.size();
    }

    private static long total(ObjectCounts counts) {
        return counts.blocks() + counts.blockEntities() + counts.entities() + counts.items();
    }

    private static String describe(ObjectCounts counts) {
        return String.format(Locale.ROOT, "%,d blocks, %,d chests or machines, %,d mobs, %,d items",
                counts.blocks(), counts.blockEntities(), counts.entities(), counts.items());
    }

    private static int backupNow(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        say(source, "[Worldsure] Saving and backing up " + worldName(server) + "...", ChatFormatting.GRAY);
        server.saveEverything(true, true, true);
        Report.BackupResult result = Worldsure.backupNow(WorldsureMc.worldDir(server), Platform.get().gameDir(), Platform.get().configDir());
        if (result.status() == Report.BackupResult.Status.CREATED) {
            source.sendSuccess(() -> Component.literal("[Worldsure] Backed up to " + result.file()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        source.sendFailure(Component.literal("[Worldsure] The backup failed: " + result.error()));
        return 0;
    }

    private static int scan(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Worldsure.rescan(index -> WorldScanMc.create(server, index));
        source.sendSuccess(() -> Component.literal("[Worldsure] Counting the whole world again in the background; "
                + "/worldsure status shows the result.").withStyle(ChatFormatting.GOLD), true);
        return 1;
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
