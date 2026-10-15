package io.github.kainrav.worldsure.fabric;

import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.List;

public final class WorldsureFabric implements ModInitializer {
    private static final String LOADER = "fabric";

    @Override
    public void onInitialize() {
        FabricLoader loader = FabricLoader.getInstance();
        // SERVER_STARTING fires before the levels load, so the backup sees the world as it was on disk.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                Worldsure.beforeWorldLoad(worldDir(server), loader.getGameDir(), loader.getConfigDir(), installedMods()));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                Worldsure.afterWorldLoad(worldDir(server), LOADER, installedMods()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> Worldsure.onWorldClosed());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!server.isDedicatedServer()) {
                showReport(handler.getPlayer(), Worldsure.takeJoinReport());
            }
        });
    }

    private static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static List<ModInfo> installedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                // Mods bundled inside another jar (the Fabric API modules, for example) come and go with their parent.
                .filter(mod -> mod.getContainingMod().isEmpty())
                .map(ModContainer::getMetadata)
                .filter(meta -> !meta.getId().equals("java"))
                .map(meta -> new ModInfo(meta.getId(), meta.getVersion().getFriendlyString()))
                .toList();
    }

    private static void showReport(ServerPlayer player, List<Report.Line> lines) {
        for (Report.Line line : lines) {
            player.sendSystemMessage(Component.literal(line.text()).withStyle(color(line.kind())));
        }
    }

    private static ChatFormatting color(Report.Kind kind) {
        return switch (kind) {
            case HEADER -> ChatFormatting.GOLD;
            case REMOVED, DOWNGRADED, BACKUP_FAILED -> ChatFormatting.RED;
            case UPDATED -> ChatFormatting.YELLOW;
            case ADDED, BACKUP_OK -> ChatFormatting.GREEN;
            case INFO -> ChatFormatting.GRAY;
        };
    }
}
