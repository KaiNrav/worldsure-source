package io.github.kainrav.worldsure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

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
 *
 * <p>The passport carries the number of its format. An older passport is upgraded when it is read
 * (see {@link #UPGRADES}). A newer one, written by a newer Worldsure, is read as far as this version
 * understands it and is never overwritten. {@code format} and {@code mods} keep their meaning in
 * every format, so any Worldsure can still compare a newer passport with the installed mods.
 */
public final class WorldManifest {
    /** The format this version writes. Changing the layout bumps it and appends a step to {@link #UPGRADES}. */
    public static final int FORMAT = 1;
    /** Brings the JSON of an older passport up to {@link #FORMAT}; empty while there is only format 1. */
    private static final Upgrades<JsonObject> UPGRADES = new Upgrades<>(FORMAT, List.of());
    public static final String DIRECTORY = "worldsure";
    public static final String FILE_NAME = "manifest.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final int format;
    private final String loader;
    private final String savedAt;
    private final Map<String, String> mods;
    private final Map<String, ObjectCounts> counts;

    public WorldManifest(String loader, String savedAt, List<ModInfo> mods) {
        this(loader, savedAt, mods, Map.of());
    }

    public WorldManifest(String loader, String savedAt, List<ModInfo> mods, Map<String, ObjectCounts> counts) {
        this(FORMAT, loader, savedAt, mods, counts);
    }

    private WorldManifest(int format, String loader, String savedAt, List<ModInfo> mods, Map<String, ObjectCounts> counts) {
        this.format = format;
        this.loader = loader;
        this.savedAt = savedAt;
        this.mods = new LinkedHashMap<>();
        mods.stream()
                .sorted(Comparator.comparing(ModInfo::id))
                .forEach(mod -> this.mods.put(mod.id(), mod.version()));
        this.counts = new TreeMap<>(counts);
    }

    /** The format the passport was stored in: {@link #FORMAT} for a new or upgraded one, higher for a newer one. */
    public int format() {
        return format;
    }

    /** Whether a newer Worldsure wrote the passport; such a passport is not written over. */
    public boolean isNewer() {
        return format > FORMAT;
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

    /**
     * Reads the passport of a world, or empty if the world has none yet. An older passport is upgraded
     * to the current format; a newer one is read as far as this version understands it.
     *
     * @throws NewerFormatException if a newer Worldsure wrote the passport and its mod list cannot be read
     * @throws IOException          if the passport cannot be read
     */
    public static Optional<WorldManifest> read(Path worldDir) throws IOException {
        Path file = path(worldDir);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            if (!json.isJsonObject()) {
                throw new IOException("Passport " + file + " is not a JSON object");
            }
            JsonObject object = json.getAsJsonObject();
            int format = format(object, file);
            if (format <= FORMAT) {
                object = UPGRADES.apply(object, format);
            }
            Stored stored;
            try {
                stored = GSON.fromJson(object, Stored.class);
            } catch (JsonParseException e) {
                if (format > FORMAT) {
                    throw new NewerFormatException(file, format, FORMAT);
                }
                throw e;
            }
            if (stored == null || stored.mods == null) {
                if (format > FORMAT) {
                    throw new NewerFormatException(file, format, FORMAT);
                }
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
            return Optional.of(new WorldManifest(Math.max(format, FORMAT), stored.loader, stored.savedAt, mods, counts));
        } catch (JsonParseException e) {
            throw new IOException("Passport " + file + " is not valid JSON", e);
        }
    }

    /** The format number of a passport; one written before the number was checked counts as format 1. */
    private static int format(JsonObject object, Path file) throws IOException {
        JsonElement format = object.get("format");
        if (format == null || format.isJsonNull()) {
            return 1;
        }
        if (format instanceof JsonPrimitive primitive && primitive.isNumber()) {
            int number = primitive.getAsInt();
            if (number >= 1 && number == primitive.getAsDouble()) {
                return number;
            }
        }
        throw new IOException("Passport " + file + " has an unknown format " + format);
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
