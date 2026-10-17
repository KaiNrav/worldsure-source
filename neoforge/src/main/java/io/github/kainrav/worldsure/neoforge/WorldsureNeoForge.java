package io.github.kainrav.worldsure.neoforge;

import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.MissingBlocks;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.nio.file.Path;
import java.util.List;

@Mod(Worldsure.MOD_ID)
public final class WorldsureNeoForge implements Platform {
    public WorldsureNeoForge(IEventBus modBus) {
        Platform.set(this);
        modBus.addListener(RegisterEvent.class, event ->
                event.register(Registries.BLOCK, helper -> helper.register(MissingBlocks.KEY, MissingBlocks.create())));
        // ServerAboutToStartEvent fires before the levels load, so the backup sees the world as it was on disk.
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> WorldsureMc.onServerStarting(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> WorldsureMc.onServerStarted(event.getServer()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> WorldsureMc.onServerStopped(event.getServer()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                WorldsureMc.onPlayerJoin(player, player.level().getServer());
            }
        });
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            WorldsureNeoForgeClient.init();
        }
    }

    @Override
    public String loaderName() {
        return "neoforge";
    }

    @Override
    public Path gameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public List<ModInfo> installedMods() {
        return ModList.get().getMods().stream()
                .map(mod -> new ModInfo(mod.getModId(), mod.getVersion().toString()))
                .toList();
    }
}
