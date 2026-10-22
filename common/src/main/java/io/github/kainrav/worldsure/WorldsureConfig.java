package io.github.kainrav.worldsure;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Settings from {@code config/worldsure.properties}. The file is created with defaults on first run,
 * and settings added by a later version are appended to an existing file with their explanation.
 *
 * @param autoBackup  back the world up before loading it when a mod was removed or changed version
 * @param keepBackups how many automatic backups to keep per world
 * @param serverOnly  use vanilla stand-ins instead of Worldsure's own block and item, so players need
 *                    no Worldsure to join (read at startup)
 * @param strict      a dedicated server refuses to start when a mod was removed or downgraded, until
 *                    the change is accepted
 */
public record WorldsureConfig(boolean autoBackup, int keepBackups, boolean serverOnly, boolean strict) {
    public static final String FILE_NAME = "worldsure.properties";
    public static final WorldsureConfig DEFAULT = new WorldsureConfig(true, 5, false, false);

    public WorldsureConfig(boolean autoBackup, int keepBackups) {
        this(autoBackup, keepBackups, DEFAULT.serverOnly, DEFAULT.strict);
    }

    public static WorldsureConfig load(Path configDir) throws IOException {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            DEFAULT.write(file);
            return DEFAULT;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        boolean autoBackup = bool(props, "autoBackup", DEFAULT.autoBackup());
        int keep;
        try {
            keep = Integer.parseInt(props.getProperty("keepBackups", Integer.toString(DEFAULT.keepBackups())).trim());
        } catch (NumberFormatException e) {
            keep = DEFAULT.keepBackups();
        }
        WorldsureConfig config = new WorldsureConfig(autoBackup, Math.max(1, keep),
                bool(props, "serverOnly", DEFAULT.serverOnly()), bool(props, "strict", DEFAULT.strict()));
        appendMissing(file, props);
        return config;
    }

    private static boolean bool(Properties props, String key, boolean fallback) {
        return Boolean.parseBoolean(props.getProperty(key, Boolean.toString(fallback)).trim());
    }

    private static String explain(String key) {
        return switch (key) {
            case "autoBackup" -> "# Back the world up before loading it when a mod was removed or changed version.\n";
            case "keepBackups" -> "# How many automatic backups to keep per world (oldest are deleted first).\n";
            case "serverOnly" -> "# Servers: true keeps blocks and items of removed mods as bedrock and barriers, so players\n"
                    + "# can join without Worldsure. Clients and single player leave it false. Read at startup.\n";
            case "strict" -> "# Dedicated servers: true refuses to start when a mod was removed or downgraded, until the\n"
                    + "# change is accepted by creating <world>/worldsure/accept-changes.\n";
            default -> "";
        };
    }

    private String value(String key) {
        return switch (key) {
            case "autoBackup" -> Boolean.toString(autoBackup);
            case "keepBackups" -> Integer.toString(keepBackups);
            case "serverOnly" -> Boolean.toString(serverOnly);
            case "strict" -> Boolean.toString(strict);
            default -> "";
        };
    }

    private static final String[] KEYS = {"autoBackup", "keepBackups", "serverOnly", "strict"};

    /** Adds settings a file written by an older version lacks, with their defaults, so they can be found. */
    private static void appendMissing(Path file, Properties props) throws IOException {
        StringBuilder missing = new StringBuilder();
        for (String key : KEYS) {
            if (!props.containsKey(key)) {
                missing.append(explain(key)).append(key).append('=').append(DEFAULT.value(key)).append('\n');
            }
        }
        if (missing.length() > 0) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String prefix = text.isEmpty() || text.endsWith("\n") ? "" : "\n";
            Files.writeString(file, prefix + missing, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# Worldsure settings\n");
            for (String key : KEYS) {
                writer.write(explain(key) + key + "=" + value(key) + "\n");
            }
        }
    }
}
