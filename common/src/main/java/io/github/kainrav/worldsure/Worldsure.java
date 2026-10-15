package io.github.kainrav.worldsure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * The loader-independent flow. Each loader calls these hooks from its own events:
 * <ol>
 *   <li>{@link #beforeWorldLoad} when the world's server is about to start, before any chunk loads;</li>
 *   <li>{@link #afterWorldLoad} once the world has started, which writes the new passport;</li>
 *   <li>{@link #takeJoinReport} when a player joins a single player world, to show them the report.</li>
 * </ol>
 */
public final class Worldsure {
    public static final String MOD_ID = "worldsure";
    public static final Logger LOG = LoggerFactory.getLogger("Worldsure");

    private static List<Report.Line> pendingReport = List.of();

    private Worldsure() {
    }

    /**
     * Compares the world's passport with the installed mods, logs the differences and backs
     * the world up when a mod was removed or changed version.
     *
     * @return the report lines, empty when nothing changed
     */
    public static synchronized List<Report.Line> beforeWorldLoad(Path worldDir, Path gameDir, Path configDir,
                                                                 List<ModInfo> installed) {
        pendingReport = List.of();
        String worldName = worldDir.getFileName().toString();
        WorldManifest saved;
        try {
            saved = WorldManifest.read(worldDir).orElse(null);
        } catch (IOException e) {
            LOG.error("Could not read the passport of world {}; skipping the check", worldName, e);
            return List.of();
        }
        if (saved == null) {
            LOG.info("World {} has no passport yet; it will be created once the world loads", worldName);
            return List.of();
        }

        ModDiff diff = ModDiff.between(saved.mods(), installed);
        if (diff.isEmpty()) {
            LOG.info("World {}: mod list unchanged ({} mods)", worldName, installed.size());
            return List.of();
        }

        Report.BackupResult backup = Report.BackupResult.NOT_NEEDED;
        if (diff.isRisky()) {
            backup = backup(worldDir, gameDir, configDir);
        }
        List<Report.Line> lines = Report.build(worldName, diff, backup);
        for (Report.Line line : lines) {
            switch (line.kind()) {
                case REMOVED, DOWNGRADED, BACKUP_FAILED -> LOG.warn(line.text());
                default -> LOG.info(line.text());
            }
        }
        pendingReport = List.copyOf(lines);
        return pendingReport;
    }

    private static Report.BackupResult backup(Path worldDir, Path gameDir, Path configDir) {
        WorldsureConfig config;
        try {
            config = WorldsureConfig.load(configDir);
        } catch (IOException e) {
            LOG.error("Could not read {}; using defaults", WorldsureConfig.FILE_NAME, e);
            config = WorldsureConfig.DEFAULT;
        }
        if (!config.autoBackup()) {
            return Report.BackupResult.DISABLED;
        }
        try {
            long start = System.nanoTime();
            Path zip = WorldBackup.create(worldDir, gameDir, config.keepBackups(), LocalDateTime.now());
            LOG.info("Backed up world in {} ms", (System.nanoTime() - start) / 1_000_000);
            return Report.BackupResult.created(gameDir.relativize(zip));
        } catch (IOException | RuntimeException e) {
            LOG.error("World backup failed", e);
            return Report.BackupResult.failed(e.getMessage());
        }
    }

    /** Records the mods the world is now running with. */
    public static void afterWorldLoad(Path worldDir, String loader, List<ModInfo> installed) {
        String now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        try {
            new WorldManifest(loader, now, installed).write(worldDir);
        } catch (IOException e) {
            LOG.error("Could not write the passport of world {}", worldDir.getFileName(), e);
        }
    }

    /** Returns the report of the world that just loaded, once; later calls get an empty list. */
    public static synchronized List<Report.Line> takeJoinReport() {
        List<Report.Line> report = pendingReport;
        pendingReport = List.of();
        return report;
    }

    /** Forgets an unshown report when the world closes. */
    public static synchronized void onWorldClosed() {
        pendingReport = List.of();
    }
}
