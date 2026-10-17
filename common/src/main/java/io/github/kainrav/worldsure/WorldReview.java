package io.github.kainrav.worldsure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the Worldsure screen shows about one world: every mod with what happened to it and
 * how much of it the world holds, and what happens (or happened) to the backup. Built before the
 * world opens, to ask the player, and again once it has loaded, for the pause menu.
 *
 * @param savedAt when the passport was last written, as stored; {@code null} for a world without one
 */
public record WorldReview(String worldName, String savedAt, List<Entry> entries, Backup backup) {

    /** Ordered from the most to the least dangerous, which is also the order the screen lists them in. */
    public enum Status { REMOVED, DOWNGRADED, UPDATED, ADDED, UNCHANGED }

    /**
     * One mod. {@code oldVersion} is empty for an added mod and {@code newVersion} for a removed one;
     * {@code counts} is what the passport counted of the mod, zero when nothing was counted.
     */
    public record Entry(String id, Status status, String oldVersion, String newVersion, ObjectCounts counts) {
    }

    public enum BackupState {
        /** Before loading: a backup will be offered because a mod was removed or changed version. */
        WILL_BACKUP,
        /** A backup would be needed, but autoBackup is off. */
        DISABLED,
        /** Nothing risky changed (or nothing changed at all). */
        NOT_NEEDED,
        CREATED,
        FAILED,
        /** The player chose to play without a backup. */
        SKIPPED,
        /** The world had no passport, so there was nothing to compare against. */
        FIRST_VISIT
    }

    /**
     * @param detail the backup file for {@link BackupState#CREATED}, the error for {@link BackupState#FAILED},
     *               otherwise empty
     */
    public record Backup(BackupState state, String detail, int keep) {
        static Backup of(Report.BackupResult result, int keep) {
            return switch (result.status()) {
                case CREATED -> new Backup(BackupState.CREATED, result.file().toString(), keep);
                case FAILED -> new Backup(BackupState.FAILED, String.valueOf(result.error()), keep);
                case DISABLED -> new Backup(BackupState.DISABLED, "", keep);
                case SKIPPED -> new Backup(BackupState.SKIPPED, "", keep);
                case NOT_NEEDED -> new Backup(BackupState.NOT_NEEDED, "", keep);
            };
        }
    }

    /** Pairs every saved mod with the installed one of the same id. */
    public static List<Entry> entries(List<ModInfo> saved, List<ModInfo> installed, Map<String, ObjectCounts> counts) {
        Map<String, String> before = toMap(saved);
        Map<String, String> now = toMap(installed);
        List<Entry> entries = new ArrayList<>();
        before.forEach((id, version) -> {
            ObjectCounts counted = counts.getOrDefault(id, ObjectCounts.ZERO);
            String nowVersion = now.get(id);
            if (nowVersion == null) {
                entries.add(new Entry(id, Status.REMOVED, version, "", counted));
            } else if (nowVersion.equals(version)) {
                entries.add(new Entry(id, Status.UNCHANGED, version, nowVersion, counted));
            } else {
                ModDiff.VersionChange change = new ModDiff.VersionChange(id, version, nowVersion);
                entries.add(new Entry(id, change.isDowngrade() ? Status.DOWNGRADED : Status.UPDATED,
                        version, nowVersion, counted));
            }
        });
        now.forEach((id, version) -> {
            if (!before.containsKey(id)) {
                entries.add(new Entry(id, Status.ADDED, "", version, counts.getOrDefault(id, ObjectCounts.ZERO)));
            }
        });
        entries.sort(Comparator.comparing(Entry::status).thenComparing(Entry::id));
        return List.copyOf(entries);
    }

    private static Map<String, String> toMap(List<ModInfo> mods) {
        Map<String, String> map = new LinkedHashMap<>();
        mods.forEach(mod -> map.put(mod.id(), mod.version()));
        return map;
    }

    public List<Entry> changes() {
        return entries.stream().filter(entry -> entry.status() != Status.UNCHANGED).toList();
    }

    public boolean hasChanges() {
        return entries.stream().anyMatch(entry -> entry.status() != Status.UNCHANGED);
    }

    /** Whether a mod was removed or changed version, which is when a backup is worth making. */
    public boolean isRisky() {
        return entries.stream().anyMatch(entry -> entry.status() == Status.REMOVED
                || entry.status() == Status.DOWNGRADED || entry.status() == Status.UPDATED);
    }

    public long count(Status status) {
        return entries.stream().filter(entry -> entry.status() == status).count();
    }
}
