package io.github.kainrav.worldsure.forge;

import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.MissingBlocks;
import io.github.kainrav.worldsure.mc.MissingItems;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.RegisterEvent;

import java.nio.file.Path;
import java.util.List;

@Mod(Worldsure.MOD_ID)
public final class WorldsureForge implements Platform {
    public WorldsureForge() {
        Platform.set(this);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterEvent event) ->
                event.register(Registries.BLOCK, helper -> helper.register(MissingBlocks.KEY, MissingBlocks.create())));
        modBus.addListener((RegisterEvent event) ->
                event.register(Registries.ITEM, helper -> helper.register(MissingItems.KEY, MissingItems.create())));
        // ServerAboutToStartEvent fires before the levels load, so the backup sees the world as it was on disk.
        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> WorldsureMc.onServerStarting(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> WorldsureMc.onServerStarted(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> WorldsureMc.onServerStopped(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                WorldsureMc.onPlayerJoin(player, player.getServer());
            }
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            WorldsureForgeClient.init();
        }
    }

    @Override
    public String loaderName() {
        return "forge";
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
