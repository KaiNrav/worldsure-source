package io.github.kainrav.worldsure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The world passport: which mods, at which versions, the world was last played with.
 * Stored as {@code <world>/worldsure/manifest.json}.
 */
public final class WorldManifest {
    public static final int FORMAT = 1;
    public static final String DIRECTORY = "worldsure";
    public static final String FILE_NAME = "manifest.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final String loader;
    private final String savedAt;
    private final Map<String, String> mods;
    private final Map<String, ObjectCounts> counts;

    public WorldManifest(String loader, String savedAt, List<ModInfo> mods) {
        this(loader, savedAt, mods, Map.of());
    }

    public WorldManifest(String loader, String savedAt, List<ModInfo> mods, Map<String, ObjectCounts> counts) {
        this.loader = loader;
        this.savedAt = savedAt;
        this.mods = new LinkedHashMap<>();
        mods.stream()
                .sorted(Comparator.comparing(ModInfo::id))
                .forEach(mod -> this.mods.put(mod.id(), mod.version()));
        this.counts = new TreeMap<>(counts);
    }

    /** Modded objects per namespace in the chunks saved so far; empty for worlds saved before counting existed. */
    public Map<String, ObjectCounts> counts() {
        return Collections.unmodifiableMap(counts);
    }

    public String loader() {
        return loader;
    }

    public String savedAt() {
        return savedAt;
    }

    public List<ModInfo> mods() {
        List<ModInfo> list = new ArrayList<>(mods.size());
        mods.forEach((id, version) -> list.add(new ModInfo(id, version)));
        return list;
    }

    public static Path path(Path worldDir) {
        return worldDir.resolve(DIRECTORY).resolve(FILE_NAME);
    }

    /** Reads the passport of a world, or empty if the world has none yet. */
    public static Optional<WorldManifest> read(Path worldDir) throws IOException {
        Path file = path(worldDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Stored stored = GSON.fromJson(reader, Stored.class);
            if (stored == null || stored.mods == null) {
                throw new IOException("Passport " + file + " has no mod list");
            }
            List<ModInfo> mods = new ArrayList<>();
            stored.mods.forEach((id, version) -> mods.add(new ModInfo(id, version == null ? "" : version)));
            Map<String, ObjectCounts> counts = new TreeMap<>();
            if (stored.counts != null) {
                stored.counts.forEach((namespace, c) -> {
                    if (c != null) {
                        counts.put(namespace, new ObjectCounts(c.blocks, c.blockEntities, c.entities));
                    }
                });
            }
            return Optional.of(new WorldManifest(stored.loader, stored.savedAt, mods, counts));
        } catch (JsonParseException e) {
            throw new IOException("Passport " + file + " is not valid JSON", e);
        }
    }

    /** Writes the passport through a temporary file so a crash never leaves half a file behind. */
    public void write(Path worldDir) throws IOException {
        Path file = path(worldDir);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        Stored stored = new Stored();
        stored.format = FORMAT;
        stored.loader = loader;
        stored.savedAt = savedAt;
        stored.mods = mods;
        stored.counts = new TreeMap<>();
        counts.forEach((namespace, c) -> {
            StoredCounts out = new StoredCounts();
            out.blocks = c.blocks();
            out.blockEntities = c.blockEntities();
            out.entities = c.entities();
            stored.counts.put(namespace, out);
        });
        try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            GSON.toJson(stored, writer);
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** JSON shape on disk. */
    private static final class Stored {
        int format;
        String loader;
        String savedAt;
        Map<String, String> mods;
        Map<String, StoredCounts> counts;
    }

    private static final class StoredCounts {
        long blocks;
        long blockEntities;
        long entities;
    }
}
