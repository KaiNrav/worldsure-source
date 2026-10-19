package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/** The server lifecycle, shared by both loaders; each loader calls these from its own events. */
public final class WorldsureMc {
    private WorldsureMc() {
    }

    /** The id of a level's dimension, like minecraft:overworld. */
    public static String dimension(ServerLevel level) {
        //#if >=1.21.11
        return level.dimension().identifier().toString();
        //#else
        //$ return level.dimension().location().toString();
        //#endif
    }

    public static long chunkKey(ChunkPos pos) {
        //#if >=26.1
        return MissingBlocks.chunkKey(pos.x(), pos.z());
        //#else
        //$ return MissingBlocks.chunkKey(pos.x, pos.z);
        //#endif
    }

    public static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    /** Before the levels load. */
    public static void onServerStarting(MinecraftServer server) {
        Platform platform = Platform.get();
        Worldsure.beforeWorldLoad(worldDir(server), platform.gameDir(), platform.configDir(), platform.installedMods());
    }

    public static void onServerStarted(MinecraftServer server) {
        Worldsure.afterWorldLoad(worldDir(server), Platform.get().loaderName(), Platform.get().installedMods());
    }

    /** After the final save. */
    public static void onServerStopped(MinecraftServer server) {
        Worldsure.onWorldClosed(worldDir(server), Platform.get().loaderName(), Platform.get().installedMods());
        MissingBlocks.clear();
    }

    public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
        if (!server.isDedicatedServer()) {
            for (Report.Line line : Worldsure.takeJoinReport()) {
                player.sendSystemMessage(Component.literal(line.text()).withStyle(color(line.kind())));
            }
        }
    }

    public static ChatFormatting color(Report.Kind kind) {
        return switch (kind) {
            case HEADER -> ChatFormatting.GOLD;
            case REMOVED, DOWNGRADED, BACKUP_FAILED -> ChatFormatting.RED;
            case UPDATED -> ChatFormatting.YELLOW;
            case ADDED, BACKUP_OK -> ChatFormatting.GREEN;
            case INFO -> ChatFormatting.GRAY;
        };
    }
}
