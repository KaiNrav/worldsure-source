package io.github.kainrav.worldsure;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    public static List<Line> build(String worldName, ModDiff diff, BackupResult backup) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Kind.HEADER, "[Worldsure] The mods of world \"" + worldName + "\" changed since it was last saved."));
        for (ModInfo mod : diff.removed()) {
            lines.add(new Line(Kind.REMOVED, "  Removed: " + mod.id() + " " + mod.version()
                    + " (its blocks, items and mobs are gone from loaded chunks)"));
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
        switch (backup.status()) {
            case CREATED -> lines.add(new Line(Kind.BACKUP_OK, "  A backup was made before loading: " + backup.file()));
            case FAILED -> lines.add(new Line(Kind.BACKUP_FAILED, "  The backup FAILED: " + backup.error()
                    + ". Quit without saving if you want to keep the world as it was."));
            case DISABLED -> lines.add(new Line(Kind.INFO, "  Automatic backups are off (config/worldsure.properties)."));
            case NOT_NEEDED -> {
            }
        }
        return lines;
    }

    public record BackupResult(Status status, Path file, String error) {
        public enum Status { CREATED, FAILED, DISABLED, NOT_NEEDED }

        public static BackupResult created(Path file) {
            return new BackupResult(Status.CREATED, file, null);
        }

        public static BackupResult failed(String error) {
            return new BackupResult(Status.FAILED, null, error);
        }

        public static final BackupResult DISABLED = new BackupResult(Status.DISABLED, null, null);
        public static final BackupResult NOT_NEEDED = new BackupResult(Status.NOT_NEEDED, null, null);
    }
}
