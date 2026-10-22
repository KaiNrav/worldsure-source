package io.github.kainrav.worldsure;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The text Worldsure shows about a changed mod list: the same lines go to the server log
 * and, in single player, to the player's chat once they join.
 */
public final class Report {
    public enum Kind { HEADER, REMOVED, DOWNGRADED, UPDATED, ADDED, BACKUP_OK, BACKUP_FAILED, INFO }

    public record Line(Kind kind, String text) {
    }

    private Report() {
    }

    public static List<Line> build(String worldName, ModDiff diff, Map<String, ObjectCounts> counts, BackupResult backup) {
        List<Line> lines = new ArrayList<>(changes(diff, counts));
        lines.add(0, new Line(Kind.HEADER, "[Worldsure] The mods of world \"" + worldName + "\" changed since it was last saved."));
        backupLine(backup, lines);
        return lines;
    }

    /** One line per removed, re-versioned and added mod, without a header. */
    public static List<Line> changes(ModDiff diff, Map<String, ObjectCounts> counts) {
        List<Line> lines = new ArrayList<>();
        for (ModInfo mod : diff.removed()) {
            lines.add(new Line(Kind.REMOVED, "  Removed: " + mod.id() + " " + mod.version()
                    + " (" + describe(counts.get(mod.id())) + ")"));
        }
        for (ModDiff.VersionChange change : diff.changed()) {
            if (change.isDowngrade()) {
                lines.add(new Line(Kind.DOWNGRADED, "  Downgraded: " + change.id() + " "
                        + change.oldVersion() + " -> " + change.newVersion() + " (older versions may not read newer data)"));
            } else {
                lines.add(new Line(Kind.UPDATED, "  Updated: " + change.id() + " "
                        + change.oldVersion() + " -> " + change.newVersion()));
            }
        }
        for (ModInfo mod : diff.added()) {
            lines.add(new Line(Kind.ADDED, "  Added: " + mod.id() + " " + mod.version()));
        }
        return lines;
    }

    /** Describes what a removed mod leaves behind, e.g. "1,204 blocks, 3 chests or machines, 2 mobs, 40 items in the world". */
    static String describe(ObjectCounts counts) {
        if (counts == null || counts.isZero()) {
            return "nothing of it counted in saved chunks";
        }
        List<String> parts = new ArrayList<>();
        if (counts.blocks() > 0) {
            parts.add(String.format(Locale.ROOT, "%,d blocks", counts.blocks()));
        }
        if (counts.blockEntities() > 0) {
            parts.add(String.format(Locale.ROOT, "%,d chests or machines", counts.blockEntities()));
        }
        if (counts.entities() > 0) {
            parts.add(String.format(Locale.ROOT, "%,d mobs or other entities", counts.entities()));
        }
        if (counts.items() > 0) {
            parts.add(String.format(Locale.ROOT, "%,d items", counts.items()));
        }
        return String.join(", ", parts) + " in the world";
    }

    /**
     * The chat lines for a player who already saw the changes on the Worldsure screen: only how the
     * backup went, which they could not see there.
     */
    public static List<Line> afterReview(BackupResult backup) {
        return switch (backup.status()) {
            case CREATED -> List.of(new Line(Kind.BACKUP_OK, "[Worldsure] Backup made before loading: " + backup.file()));
            case FAILED -> List.of(new Line(Kind.BACKUP_FAILED, "[Worldsure] The backup FAILED: " + backup.error()
                    + ". Quit without saving if you want to keep the world as it was."));
            case SKIPPED -> List.of(new Line(Kind.INFO, "[Worldsure] Loaded without a backup, as chosen."));
            case DISABLED, NOT_NEEDED -> List.of();
        };
    }

    private static void backupLine(BackupResult backup, List<Line> lines) {
        switch (backup.status()) {
            case CREATED -> lines.add(new Line(Kind.BACKUP_OK, "  A backup was made before loading: " + backup.file()));
            case FAILED -> lines.add(new Line(Kind.BACKUP_FAILED, "  The backup FAILED: " + backup.error()
                    + ". Quit without saving if you want to keep the world as it was."));
            case DISABLED -> lines.add(new Line(Kind.INFO, "  Automatic backups are off (config/worldsure.properties)."));
            case SKIPPED -> lines.add(new Line(Kind.INFO, "  Loaded without a backup, as chosen."));
            case NOT_NEEDED -> {
            }
        }
    }

    public record BackupResult(Status status, Path file, String error) {
        public enum Status { CREATED, FAILED, DISABLED, SKIPPED, NOT_NEEDED }

        public static BackupResult created(Path file) {
            return new BackupResult(Status.CREATED, file, null);
        }

        public static BackupResult failed(String error) {
            return new BackupResult(Status.FAILED, null, error);
        }

        public static final BackupResult DISABLED = new BackupResult(Status.DISABLED, null, null);
        public static final BackupResult SKIPPED = new BackupResult(Status.SKIPPED, null, null);
        public static final BackupResult NOT_NEEDED = new BackupResult(Status.NOT_NEEDED, null, null);
    }
}
