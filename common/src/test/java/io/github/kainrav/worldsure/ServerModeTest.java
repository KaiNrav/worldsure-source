package io.github.kainrav.worldsure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerModeTest {
    @TempDir
    Path game;

    private Path config() {
        return game.resolve("config");
    }

    private void strict(boolean on) throws IOException {
        Files.createDirectories(config());
        Files.writeString(config().resolve(WorldsureConfig.FILE_NAME), "strict=" + on + "\n");
    }

    private Path world(List<ModInfo> mods) throws IOException {
        Path world = game.resolve("world");
        Files.createDirectories(world);
        new WorldManifest("fabric", "x", mods).write(world);
        return world;
    }

    @Test
    void anOlderConfigGetsTheNewSettingsAppended() throws IOException {
        Files.createDirectories(config());
        Path file = config().resolve(WorldsureConfig.FILE_NAME);
        Files.writeString(file, "# mine\nautoBackup=false\nkeepBackups=9");
        WorldsureConfig config = WorldsureConfig.load(config());
        assertEquals(new WorldsureConfig(false, 9, false, false), config);
        String text = Files.readString(file);
        assertTrue(text.startsWith("# mine\nautoBackup=false\nkeepBackups=9\n"), text);
        assertTrue(text.contains("serverOnly=false") && text.contains("strict=false"), text);
        WorldsureConfig.load(config());
        assertEquals(text, Files.readString(file), "added once");
    }

    @Test
    void savedSettingsReadBack() throws IOException {
        WorldsureConfig changed = new WorldsureConfig(false, 20, true, true);
        changed.save(config());
        assertEquals(changed, WorldsureConfig.load(config()));
        String text = Files.readString(config().resolve(WorldsureConfig.FILE_NAME));
        assertTrue(text.contains("# Servers: true keeps blocks and items"), "explanations are kept: " + text);
    }

    @Test
    void readsServerSettings() throws IOException {
        Files.createDirectories(config());
        Files.writeString(config().resolve(WorldsureConfig.FILE_NAME), "serverOnly=true\nstrict=TRUE\n");
        WorldsureConfig config = WorldsureConfig.load(config());
        assertTrue(config.serverOnly());
        assertTrue(config.strict());
        assertTrue(config.autoBackup());
    }

    @Test
    void strictModeStopsRemovedAndOlderModsUntilAccepted() throws IOException {
        List<ModInfo> installed = List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "19.0"));
        Path world = world(List.of(new ModInfo("minecraft", "26.3"), new ModInfo("create", "6.0"), new ModInfo("jei", "19.2")));

        strict(false);
        assertTrue(Worldsure.strictRefusal(world, config(), installed).isEmpty(), "off by default");

        strict(true);
        String refusal = Worldsure.strictRefusal(world, config(), installed).orElseThrow();
        assertTrue(refusal.contains("removed: create 6.0"), refusal);
        assertTrue(refusal.contains("downgraded: jei 19.2 -> 19.0"), refusal);
        assertTrue(refusal.contains(Worldsure.ACCEPT_FILE), refusal);

        Path accept = world.resolve(WorldManifest.DIRECTORY).resolve(Worldsure.ACCEPT_FILE);
        Files.writeString(accept, "");
        assertTrue(Worldsure.strictRefusal(world, config(), installed).isEmpty(), "accepted");
        assertFalse(Files.exists(accept), "the acceptance is used up");
        assertTrue(Worldsure.strictRefusal(world, config(), installed).isPresent(), "the next start asks again");
    }

    @Test
    void strictModeLetsUpdatesAndNewWorldsThrough() throws IOException {
        strict(true);
        List<ModInfo> installed = List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "19.3"), new ModInfo("emi", "1"));
        Path world = world(List.of(new ModInfo("minecraft", "26.3"), new ModInfo("jei", "19.2")));
        assertTrue(Worldsure.strictRefusal(world, config(), installed).isEmpty(), "an update and an added mod are fine");
        assertTrue(Worldsure.strictRefusal(game.resolve("fresh"), config(), installed).isEmpty(), "a new world is fine");
    }
}
