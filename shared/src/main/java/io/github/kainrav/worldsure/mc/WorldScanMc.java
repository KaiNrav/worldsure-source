package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.CountIndex;
import io.github.kainrav.worldsure.WorldScan;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.storage.LevelStorageSource;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The Minecraft side of a {@link WorldScan}: where each dimension's files are and how a stored chunk is counted. */
public final class WorldScanMc {
    /** Reads stored chunks with the game's NBT reader and counts them the way chunk saves are counted. */
    static final WorldScan.Counter COUNTER = new WorldScan.Counter() {
        @Override
        public Map<String, long[]> blocks(InputStream nbt) throws IOException {
            CompoundTag chunk = read(nbt);
            // Chunks last saved before 1.18 keep everything under "Level"; they are counted once the game saves them.
            return chunk.contains("sections") ? MissingBlocks.count(chunk) : null;
        }

        @Override
        public Map<String, long[]> entities(InputStream nbt) throws IOException {
            CompoundTag chunk = read(nbt);
            return chunk.contains("Entities") ? MissingEntities.countWithItems(chunk) : null;
        }
    };

    private WorldScanMc() {
    }

    /** A scan of every dimension of a running server, feeding {@code index}. */
    public static WorldScan create(MinecraftServer server, CountIndex index) {
        return new WorldScan(dimensions(server), index, COUNTER, WorldScanMc::decompress);
    }

    /** Every dimension of a running server with its chunk and entity folders, which may not exist yet. */
    static List<WorldScan.Dimension> dimensions(MinecraftServer server) {
        LevelStorageSource.LevelStorageAccess storage = storage(server);
        List<WorldScan.Dimension> dimensions = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            Path folder = storage.getDimensionPath(level.dimension());
            dimensions.add(new WorldScan.Dimension(WorldsureMc.dimension(level), folder.resolve("region"), folder.resolve("entities")));
        }
        return dimensions;
    }

    /** LZ4 and whatever else the game knows beyond what the JDK reads. */
    static InputStream decompress(int method, InputStream compressed) throws IOException {
        // 127 is a custom compression named in the chunk itself; nothing here reads it.
        if (method == 127 || !RegionFileVersion.isValidVersion(method)) {
            return null;
        }
        return RegionFileVersion.fromId(method).wrap(compressed);
    }

    static CompoundTag read(InputStream nbt) throws IOException {
        return NbtIo.read(new DataInputStream(new BufferedInputStream(nbt)));
    }

    /** The server's access to its world folder; a protected field, found by its type so any names work. */
    private static LevelStorageSource.LevelStorageAccess storage(MinecraftServer server) {
        for (Class<?> c = server.getClass(); c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getType() == LevelStorageSource.LevelStorageAccess.class && !Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        return (LevelStorageSource.LevelStorageAccess) field.get(server);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        throw new IllegalStateException("Cannot reach the world folder of the server", e);
                    }
                }
            }
        }
        throw new IllegalStateException("The server has no world folder access");
    }
}
