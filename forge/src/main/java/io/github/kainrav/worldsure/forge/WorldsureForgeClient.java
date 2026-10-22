package io.github.kainrav.worldsure.forge;

import io.github.kainrav.worldsure.mc.client.SettingsScreen;
import io.github.kainrav.worldsure.mc.client.WorldsureClient;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/** Kept apart from {@link WorldsureForge} so a dedicated server never loads client classes. */
final class WorldsureForgeClient {
    private WorldsureForgeClient() {
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) ->
                WorldsureClient.pauseMenuButton(event.getScreen()).ifPresent(event::addListener));
        // The Config button in the mod list.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new SettingsScreen(parent)));
    }
}
