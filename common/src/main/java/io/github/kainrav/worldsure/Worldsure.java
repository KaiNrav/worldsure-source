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
 *   <li>{@link #review} on the client when the player opens a world, to show the Worldsure screen;</li>
 *   <li>{@link #beforeWorldLoad} when the world's server is about to start, before any chunk loads;</li>
 *   <li>{@link #afterWorldLoad} once the world has started, which writes the new passport;</li>
 *   <li>{@link #takeJoinReport} when a player joins a single player world, to show them the report;</li>
 *   <li>{@link #onWorldClosed} after the world's final save, which stores the object counts.</li>
 * </ol>
 * A passport or count index written by a newer Worldsure is never written over, so going back to
 * that version loses nothing.
 */
public final class Worldsure {
    public static final String MOD_ID = "worldsure";
    public static final Logger LOG = LoggerFactory.getLogger("Worldsure");

    private static List<Report.Line> pendingReport = List.of();
    /** Choices made on the confirmation screen, by world folder, consumed when that world's server starts. */
    private static final Map<Path, Report.BackupResult> decisions = new ConcurrentHashMap<>();
    private static volatile CountIndex counts = new CountIndex();
    /** Whether the open world's count index is from a newer Worldsure and must stay as it is. */
    private static volatile boolean keepCountIndex;
    private static WorldReview currentReview;

    private Worldsure() {
    }

    /** What changed in a world's mods, with the counts saved for it. */
    public record Check(String worldName, ModDiff diff, Map<String, ObjectCounts> counts) {
    }

    /** Compares a world's passport with the installed mods; empty when it has no passport or nothing changed. */
    public static Optional<Check> check(Path worldDir, List<ModInfo> installed) {
        String worldName = worldDir.getFileName().toString();
        WorldManifest saved;
        try {
            saved = WorldManifest.read(worldDir).orElse(null);
        } catch (NewerFormatException e) {
            LOG.warn("World {} was last played with a newer Worldsure (passport format {}, this one reads up to {}); "
                    + "skipping the check and leaving the passport as it is", worldName, e.found(), e.known());
            return Optional.empty();
        } catch (IOException e) {
            LOG.error("Could not read the passport of world {}; skipping the check", worldName, e);
            return Optional.empty();
        }
        if (saved != null && saved.isNewer()) {
            LOG.warn("World {} was last played with a newer Worldsure (passport format {}, this one reads up to {}); "
                    + "its passport is left as it is", worldName, saved.format(), WorldManifest.FORMAT);
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
        WorldsureConfig config = loadConfig(configDir);
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
        currentReview = null;
        Report.BackupResult decided = decisions.remove(worldDir.toAbsolutePath().normalize());
        loadCounts(worldDir);
        int keep = loadConfig(configDir).keepBackups();
        // Read before afterWorldLoad overwrites the passport; the pause menu shows it while the world is open.
        WorldReview review = review(worldDir, configDir, installed).orElse(null);

        Optional<Check> check = check(worldDir, installed);
        if (check.isEmpty()) {
            // Either nothing changed, or the world has no passport yet.
            currentReview = review != null
                    ? new WorldReview(review.worldName(), review.savedAt(), review.entries(),
                            new WorldReview.Backup(WorldReview.BackupState.NOT_NEEDED, "", keep))
                    : new WorldReview(worldDir.getFileName().toString(), null,
                            WorldReview.entries(installed, installed, Map.of()),
                            new WorldReview.Backup(WorldReview.BackupState.FIRST_VISIT, "", keep));
            return List.of();
        }
        ModDiff diff = check.get().diff();
        Report.BackupResult backup = Report.BackupResult.NOT_NEEDED;
        if (decided != null) {
            backup = decided;
        } else if (diff.isRisky()) {
            backup = backup(worldDir, gameDir, configDir);
        }
        if (review != null) {
            currentReview = new WorldReview(review.worldName(), review.savedAt(), review.entries(),
                    WorldReview.Backup.of(backup, keep));
        }
        List<Report.Line> lines = Report.build(check.get().worldName(), diff, check.get().counts(), backup);
        for (Report.Line line : lines) {
            switch (line.kind()) {
                case REMOVED, DOWNGRADED, BACKUP_FAILED -> LOG.warn(line.text());
                default -> LOG.info(line.text());
            }
        }
        // A player who decided on the Worldsure screen has seen the changes; chat only needs the backup outcome.
        pendingReport = decided != null ? Report.afterReview(backup) : List.copyOf(lines);
        return List.copyOf(lines);
    }

    /**
     * Compares the world's passport with the installed mods without touching anything, for the screen
     * shown before the world opens.
     *
     * @return empty when the world has no passport yet or it cannot be read
     */
    public static Optional<WorldReview> review(Path worldDir, Path configDir, List<ModInfo> installed) {
        WorldManifest saved;
        try {
            saved = WorldManifest.read(worldDir).orElse(null);
        } catch (NewerFormatException e) {
            // check() explains this in the log when the world starts.
            return Optional.empty();
        } catch (IOException e) {
            LOG.error("Could not read the passport of world {}", worldDir.getFileName(), e);
            return Optional.empty();
        }
        if (saved == null) {
            return Optional.empty();
        }
        WorldsureConfig config = loadConfig(configDir);
        List<WorldReview.Entry> entries = WorldReview.entries(saved.mods(), installed, saved.counts());
        WorldReview review = new WorldReview(worldDir.getFileName().toString(), saved.savedAt(), entries, null);
        WorldReview.BackupState state;
        if (!review.isRisky()) {
            state = WorldReview.BackupState.NOT_NEEDED;
        } else if (config.autoBackup()) {
            state = WorldReview.BackupState.WILL_BACKUP;
        } else {
            state = WorldReview.BackupState.DISABLED;
        }
        return Optional.of(new WorldReview(review.worldName(), review.savedAt(), entries,
                new WorldReview.Backup(state, "", config.keepBackups())));
    }

    /** The review of the world that is open now; empty when none is open in this game. */
    public static synchronized Optional<WorldReview> currentReview() {
        return Optional.ofNullable(currentReview);
    }

    private static WorldsureConfig loadConfig(Path configDir) {
        try {
            return WorldsureConfig.load(configDir);
        } catch (IOException e) {
            LOG.error("Could not read {}; using defaults", WorldsureConfig.FILE_NAME, e);
            return WorldsureConfig.DEFAULT;
        }
    }

    private static void loadCounts(Path worldDir) {
        keepCountIndex = false;
        try {
            counts = CountIndex.read(worldDir);
        } catch (NewerFormatException e) {
            LOG.warn("The object counts of world {} were saved by a newer Worldsure (format {}, this one reads up to {}); "
                    + "they are left as they are and this session's counts are not saved", worldDir.getFileName(), e.found(), e.known());
            counts = new CountIndex();
            keepCountIndex = true;
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
        if (hasNewerPassport(worldDir)) {
            return;
        }
        String now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        try {
            new WorldManifest(loader, now, installed, counts.totals()).write(worldDir);
        } catch (IOException e) {
            LOG.error("Could not write the passport of world {}", worldDir.getFileName(), e);
        }
    }

    private static boolean hasNewerPassport(Path worldDir) {
        try {
            return WorldManifest.read(worldDir).map(WorldManifest::isNewer).orElse(false);
        } catch (NewerFormatException e) {
            return true;
        } catch (IOException e) {
            // A damaged passport is replaced by a fresh one.
            return false;
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
        currentReview = null;
        try {
            if (!keepCountIndex) {
                counts.write(worldDir);
            }
        } catch (IOException e) {
            LOG.error("Could not write the object counts of world {}", worldDir.getFileName(), e);
        }
        writePassport(worldDir, loader, installed);
    }
}
