package io.github.kainrav.worldsure.neoforge;

import io.github.kainrav.worldsure.mc.client.WorldsureClient;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Kept apart from {@link WorldsureNeoForge} so a dedicated server never loads client classes. */
final class WorldsureNeoForgeClient {
    private WorldsureNeoForgeClient() {
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, event ->
                WorldsureClient.pauseMenuButton(event.getScreen()).ifPresent(event::addListener));
    }
}
