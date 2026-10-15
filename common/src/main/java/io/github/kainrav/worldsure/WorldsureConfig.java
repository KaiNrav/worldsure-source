package io.github.kainrav.worldsure;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings from {@code config/worldsure.properties}; the file is created with defaults on first run. */
public record WorldsureConfig(boolean autoBackup, int keepBackups) {
    public static final String FILE_NAME = "worldsure.properties";
    public static final WorldsureConfig DEFAULT = new WorldsureConfig(true, 5);

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
        boolean autoBackup = Boolean.parseBoolean(
                props.getProperty("autoBackup", Boolean.toString(DEFAULT.autoBackup())).trim());
        int keep;
        try {
            keep = Integer.parseInt(props.getProperty("keepBackups", Integer.toString(DEFAULT.keepBackups())).trim());
        } catch (NumberFormatException e) {
            keep = DEFAULT.keepBackups();
        }
        return new WorldsureConfig(autoBackup, Math.max(1, keep));
    }

    private void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write("# Worldsure settings\n");
            writer.write("# Back the world up before loading it when a mod was removed or changed version.\n");
            writer.write("autoBackup=" + autoBackup + "\n");
            writer.write("# How many automatic backups to keep per world (oldest are deleted first).\n");
            writer.write("keepBackups=" + keepBackups + "\n");
        }
    }
}
