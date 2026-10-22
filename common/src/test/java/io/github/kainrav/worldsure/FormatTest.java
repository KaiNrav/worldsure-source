package io.github.kainrav.worldsure;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatTest {
    private static final List<ModInfo> INSTALLED = List.of(new ModInfo("minecraft", "26.3"), new ModInfo("worldsure", "0.1.1"));

    @TempDir
    Path game;

    private Path world() throws IOException {
        Path world = game.resolve("saves").resolve("World");
        Files.createDirectories(world);
        Files.writeString(world.resolve("level.dat"), "level");
        return world;
    }

    private Path config() {
        return game.resolve("config");
    }

    private void writePassport(Path world, String json) throws IOException {
        Files.createDirectories(WorldManifest.path(world).getParent());
        Files.writeString(WorldManifest.path(world), json);
    }

    /** Opens and closes the world the way the game does, with one counted chunk. */
    private void playOnce(Path world) {
        Worldsure.beforeWorldLoad(world, game, config(), INSTALLED);
        Worldsure.afterWorldLoad(world, "fabric", INSTALLED);
        Worldsure.counts().putBlocks("minecraft:overworld", CountIndex.chunkKey(0, 0), Map.of("create", new long[]{3, 0}));
        Worldsure.onWorldClosed(world, "fabric", INSTALLED);
    }

    private long backups() throws IOException {
        Path dir = WorldBackup.backupsDir(game);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var files = Files.list(dir)) {
            return files.count();
        }
    }

    @Test
    void upgradesRunInOrderFromTheStoredFormat() {
        List<UnaryOperator<String>> steps = List.of(s -> s + ">2", s -> s + ">3");
        Upgrades<String> upgrades = new Upgrades<>(3, steps);
        assertEquals("1>2>3", upgrades.apply("1", 1));
        assertEquals("2>3", upgrades.apply("2", 2));
        assertEquals("3", upgrades.apply("3", 3));
        assertThrows(IllegalArgumentException.class, () -> upgrades.apply("4", 4));
        assertThrows(IllegalArgumentException.class, () -> upgrades.apply("0", 0));
        assertThrows(IllegalArgumentException.class, () -> new Upgrades<>(3, List.<UnaryOperator<String>>of(s -> s)));
    }

    @Test
    void passportStoresItsFormat() throws IOException {
        Path world = world();
        new WorldManifest("fabric", "x", INSTALLED).write(world);
        JsonObject json = JsonParser.parseString(Files.readString(WorldManifest.path(world))).getAsJsonObject();
        assertEquals(WorldManifest.FORMAT, json.get("format").getAsInt());

        WorldManifest read = WorldManifest.read(world).orElseThrow();
        assertEquals(WorldManifest.FORMAT, read.format());
        assertFalse(read.isNewer());
    }

    @Test
    void passportWithoutFormatIsTheFirstFormat() throws IOException {
        Path world = world();
        writePassport(world, "{\"loader\": \"fabric\", \"mods\": {\"create\": \"6\"}}");
        WorldManifest read = WorldManifest.read(world).orElseThrow();
        assertEquals(1, read.format());
        assertEquals(List.of(new ModInfo("create", "6")), read.mods());
    }

    @Test
    void passportWithAnUnknownFormatIsRejected() throws IOException {
        Path world = world();
        for (String format : List.of("\"two\"", "0", "-1", "1.5", "[1]")) {
            writePassport(world, "{\"format\": " + format + ", \"mods\": {}}");
            IOException e = assertThrows(IOException.class, () -> WorldManifest.read(world), format);
            assertFalse(e instanceof NewerFormatException, format);
        }
    }

    @Test
    void newerPassportIsComparedButNotWrittenOver() throws IOException {
        Path world = world();
        String newer = "{\"format\": " + (WorldManifest.FORMAT + 1) + ", \"loader\": \"fabric\", \"savedAt\": \"later\","
                + " \"somethingNew\": {\"a\": [1, 2]},"
                + " \"mods\": {\"minecraft\": \"26.3\", \"worldsure\": \"0.2.0\", \"create\": \"6\"}}";
        writePassport(world, newer);

        WorldManifest read = WorldManifest.read(world).orElseThrow();
        assertTrue(read.isNewer());
        assertEquals(WorldManifest.FORMAT + 1, read.format());
        assertEquals(3, read.mods().size());

        // create is gone and Worldsure went back a version: the world is backed up as usual.
        List<Report.Line> report = Worldsure.beforeWorldLoad(world, game, config(), INSTALLED);
        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.REMOVED && line.text().contains("create")));
        assertTrue(report.stream().anyMatch(line -> line.kind() == Report.Kind.DOWNGRADED && line.text().contains("worldsure")));
        assertEquals(1, backups());
        Worldsure.afterWorldLoad(world, "fabric", INSTALLED);
        Worldsure.onWorldClosed(world, "fabric", INSTALLED);

        assertEquals(newer, Files.readString(WorldManifest.path(world)));
    }

    @Test
    void unreadableNewerPassportIsLeftAlone() throws IOException {
        Path world = world();
        String newer = "{\"format\": " + (WorldManifest.FORMAT + 5) + ", \"mods\": [\"create\"]}";
        writePassport(world, newer);
        NewerFormatException e = assertThrows(NewerFormatException.class, () -> WorldManifest.read(world));
        assertEquals(WorldManifest.FORMAT + 5, e.found());
        assertEquals(WorldManifest.FORMAT, e.known());

        playOnce(world);
        assertEquals(newer, Files.readString(WorldManifest.path(world)));
    }

    @Test
    void damagedPassportIsReplaced() throws IOException {
        Path world = world();
        writePassport(world, "{\"format\": 1, \"mods\": ");
        playOnce(world);
        assertEquals(INSTALLED, WorldManifest.read(world).orElseThrow().mods());
    }

    @Test
    void newerCountIndexIsLeftAlone() throws IOException {
        Path world = world();
        Path index = CountIndex.path(world);
        Files.createDirectories(index.getParent());
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(index)))) {
            out.writeInt(CountIndex.FORMAT + 1);
            out.writeUTF("a layout this version does not know");
        }
        byte[] before = Files.readAllBytes(index);
        assertThrows(NewerFormatException.class, () -> CountIndex.read(world));

        playOnce(world);
        assertArrayEquals(before, Files.readAllBytes(index));
        // The passport is still this version's and carries this session's counts.
        assertEquals(new ObjectCounts(3, 0, 0), WorldManifest.read(world).orElseThrow().counts().get("create"));

        // Once the index is readable again, counting is saved as before.
        Files.delete(index);
        playOnce(world);
        assertEquals(new ObjectCounts(3, 0, 0), CountIndex.read(world).totals().get("create"));
    }
}
