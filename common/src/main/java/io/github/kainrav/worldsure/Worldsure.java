package io.github.kainrav.worldsure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loader-independent flow. Each loader calls these hooks from its own events:
 * <ol>
 *   <li>{@link #check} on the client when the player opens a world, to show the confirmation screen;</li>
 *   <li>{@link #beforeWorldLoad} when the world's server is about to start, before any chunk loads;</li>
 *   <li>{@link #afterWorldLoad} once the world has started, which writes the new passport;</li>
 *   <li>{@link #takeJoinReport} when a player joins a single player world, to show them the report;</li>
 *   <li>{@link #onWorldClosed} after the world's final save, which stores the object counts.</li>
 * </ol>
 */
public final class Worldsure {
    public static final String MOD_ID = "worldsure";
    public static final Logger LOG = LoggerFactory.getLogger("Worldsure");

    private static List<Report.Line> pendingReport = List.of();
    /** Choices made on the confirmation screen, by world folder, consumed when that world's server starts. */
    private static final Map<Path, Report.BackupResult> decisions = new ConcurrentHashMap<>();
    private static volatile CountIndex counts = new CountIndex();

    private Worldsure() {
    }

    /** What changed in a world's mods, with the counts saved for it. */
    public record Check(String worldName, ModDiff diff, Map<String, ObjectCounts> counts) {
        public List<Report.Line> lines() {
            return Report.changes(diff, counts);
        }
    }

    /** Compares a world's passport with the installed mods; empty when it has no passport or nothing changed. */
    public static Optional<Check> check(Path worldDir, List<ModInfo> installed) {
        String worldName = worldDir.getFileName().toString();
        WorldManifest saved;
        try {
            saved = WorldManifest.read(worldDir).orElse(null);
        } catch (IOException e) {
            LOG.error("Could not read the passport of world {}; skipping the check", worldName, e);
            return Optional.empty();
        }
        if (saved == null) {
            LOG.info("World {} has no passport yet; it will be created once the world loads", worldName);
            return Optional.empty();
        }
        ModDiff diff = ModDiff.between(saved.mods(), installed);
        if (diff.isEmpty()) {
            LOG.info("World {}: mod list unchanged ({} mods)", worldName, installed.size());
            return Optional.empty();
        }
        return Optional.of(new Check(worldName, diff, saved.counts()));
    }

    /** Backs a world up now, honouring the config; used by the confirmation screen and the server start. */
    public static Report.BackupResult backup(Path worldDir, Path gameDir, Path configDir) {
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
            LOG.info("Backed up world {} in {} ms", worldDir.getFileName(), (System.nanoTime() - start) / 1_000_000);
            return Report.BackupResult.created(gameDir.relativize(zip));
        } catch (IOException | RuntimeException e) {
            LOG.error("World backup failed", e);
            return Report.BackupResult.failed(e.getMessage());
        }
    }

    /** Remembers what the player chose on the confirmation screen, so the server start does not ask or back up again. */
    public static void rememberDecision(Path worldDir, Report.BackupResult backup) {
        decisions.put(worldDir.toAbsolutePath().normalize(), backup);
    }

    /**
     * Compares the world's passport with the installed mods, logs the differences and backs
     * the world up when a mod was removed or changed version, unless the player already decided
     * on the confirmation screen. Also loads the world's object counts.
     *
     * @return the report lines, empty when nothing changed
     */
    public static synchronized List<Report.Line> beforeWorldLoad(Path worldDir, Path gameDir, Path configDir,
                                                                 List<ModInfo> installed) {
        pendingReport = List.of();
        Report.BackupResult decided = decisions.remove(worldDir.toAbsolutePath().normalize());
        loadCounts(worldDir);

        Optional<Check> check = check(worldDir, installed);
        if (check.isEmpty()) {
            return List.of();
        }
        ModDiff diff = check.get().diff();
        Report.BackupResult backup = Report.BackupResult.NOT_NEEDED;
        if (decided != null) {
            backup = decided;
        } else if (diff.isRisky()) {
            backup = backup(worldDir, gameDir, configDir);
        }
        List<Report.Line> lines = Report.build(check.get().worldName(), diff, check.get().counts(), backup);
        for (Report.Line line : lines) {
            switch (line.kind()) {
                case REMOVED, DOWNGRADED, BACKUP_FAILED -> LOG.warn(line.text());
                default -> LOG.info(line.text());
            }
        }
        pendingReport = List.copyOf(lines);
        return pendingReport;
    }

    private static void loadCounts(Path worldDir) {
        try {
            counts = CountIndex.read(worldDir);
        } catch (IOException e) {
            LOG.error("Could not read the object counts of world {}; counting starts over", worldDir.getFileName(), e);
            counts = new CountIndex();
        }
    }

    /** The counts of the world that is running now; the game's chunk saving feeds them. */
    public static CountIndex counts() {
        return counts;
    }

    /** Records the mods the world is now running with. */
    public static void afterWorldLoad(Path worldDir, String loader, List<ModInfo> installed) {
        writePassport(worldDir, loader, installed);
    }

    private static void writePassport(Path worldDir, String loader, List<ModInfo> installed) {
        String now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        try {
            new WorldManifest(loader, now, installed, counts.totals()).write(worldDir);
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

    /** Stores the counts and the passport after the world's final save, and forgets an unshown report. */
    public static synchronized void onWorldClosed(Path worldDir, String loader, List<ModInfo> installed) {
        pendingReport = List.of();
        try {
            counts.write(worldDir);
        } catch (IOException e) {
            LOG.error("Could not write the object counts of world {}", worldDir.getFileName(), e);
        }
        writePassport(worldDir, loader, installed);
    }
}
