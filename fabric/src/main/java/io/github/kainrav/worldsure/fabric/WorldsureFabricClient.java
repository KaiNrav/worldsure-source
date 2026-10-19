package io.github.kainrav.worldsure.fabric;

import io.github.kainrav.worldsure.mc.client.WorldsureClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

public final class WorldsureFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                //#if >=26.1
                WorldsureClient.pauseMenuButton(screen).ifPresent(Screens.getWidgets(screen)::add));
                //#else
                //$ WorldsureClient.pauseMenuButton(screen).ifPresent(Screens.getButtons(screen)::add));
                //#endif
    }
}
