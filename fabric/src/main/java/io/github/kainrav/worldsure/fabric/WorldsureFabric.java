package io.github.kainrav.worldsure.fabric;

import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.mc.MissingBlocks;
import io.github.kainrav.worldsure.mc.MissingItems;
import io.github.kainrav.worldsure.mc.Placeholders;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.WorldsureCommands;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.nio.file.Path;
import java.util.List;

public final class WorldsureFabric implements ModInitializer, Platform {
    @Override
    public void onInitialize() {
        Platform.set(this);
        WorldsureMc.init(configDir());
        if (!Placeholders.serverOnly()) {
            Registry.register(BuiltInRegistries.BLOCK, MissingBlocks.KEY, MissingBlocks.create());
            Registry.register(BuiltInRegistries.ITEM, MissingItems.KEY, MissingItems.create());
        }
        // SERVER_STARTING fires before the levels load, so the backup sees the world as it was on disk.
        ServerLifecycleEvents.SERVER_STARTING.register(WorldsureMc::onServerStarting);
        ServerLifecycleEvents.SERVER_STARTED.register(WorldsureMc::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPED.register(WorldsureMc::onServerStopped);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                WorldsureMc.onPlayerJoin(handler.getPlayer(), server));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WorldsureCommands.register(dispatcher));
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            WorldsureMc.onDedicatedServerInit(gameDir(), configDir());
        }
    }

    @Override
    public String loaderName() {
        return "fabric";
    }

    @Override
    public Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public List<ModInfo> installedMods() {
        return FabricLoader.getInstance().getAllMods().stream()
                // Mods bundled inside another jar (the Fabric API modules, for example) come and go with their parent.
                .filter(mod -> mod.getContainingMod().isEmpty())
                .map(ModContainer::getMetadata)
                .filter(meta -> !meta.getId().equals("java"))
                .map(meta -> new ModInfo(meta.getId(), meta.getVersion().getFriendlyString()))
                .toList();
    }
}
