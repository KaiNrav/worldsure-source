package io.github.kainrav.worldsure.neoforge;

import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.nio.file.Path;
import java.util.List;

@Mod(Worldsure.MOD_ID)
public final class WorldsureNeoForge {
    private static final String LOADER = "neoforge";

    public WorldsureNeoForge() {
        // ServerAboutToStartEvent fires before the levels load, so the backup sees the world as it was on disk.
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event ->
                Worldsure.beforeWorldLoad(worldDir(event.getServer()), FMLPaths.GAMEDIR.get(),
                        FMLPaths.CONFIGDIR.get(), installedMods()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event ->
                Worldsure.afterWorldLoad(worldDir(event.getServer()), LOADER, installedMods()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> Worldsure.onWorldClosed());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && !player.level().getServer().isDedicatedServer()) {
                showReport(player, Worldsure.takeJoinReport());
            }
        });
    }

    private static Path worldDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
    }

    private static List<ModInfo> installedMods() {
        return ModList.get().getMods().stream()
                .map(mod -> new ModInfo(mod.getModId(), mod.getVersion().toString()))
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
