package io.github.kainrav.worldsure;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips a world folder into {@code <game>/backups/worldsure/}, prunes old copies, lists them and
 * restores one. A restore never deletes the world it replaces: that folder is renamed to
 * {@code <world>_before_restore_<time>} next to it.
 */
public final class WorldBackup {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    /** Asks for a backup to be restored the next time its world is about to load. */
    public static final String PENDING_FILE = "restore-pending.properties";

    /** One automatic backup of a world. */
    public record Backup(Path file, LocalDateTime time, long size) {
        public String name() {
            return file.getFileName().toString();
        }
    }

    /** A restore asked for and not done yet: which world folder, from which backup file. */
    public record PendingRestore(String world, String backup) {
    }
    /** Held open by the running game; copying it is pointless and fails on Windows. */
    private static final String SESSION_LOCK = "session.lock";

    private WorldBackup() {
    }

    public static Path backupsDir(Path gameDir) {
        return gameDir.resolve("backups").resolve("worldsure");
    }

    /**
     * Creates a zip of {@code worldDir} and keeps only the newest {@code keep} backups of that world.
     *
     * @return the zip that was written
     * @throws IOException if the disk is too full or the copy fails; a partial zip is removed
     */
    public static Path create(Path worldDir, Path gameDir, int keep, LocalDateTime now) throws IOException {
        Path targetDir = backupsDir(gameDir);
        Files.createDirectories(targetDir);
        String worldName = worldDir.getFileName().toString();

        long worldSize = sizeOf(worldDir);
        long free = Files.getFileStore(targetDir).getUsableSpace();
        // Zipped worlds are usually much smaller, but a full copy is the worst case.
        if (free < worldSize) {
            throw new IOException("Not enough disk space for a backup of " + worldName
                    + ": need up to " + worldSize / (1024 * 1024) + " MB, " + free / (1024 * 1024) + " MB free");
        }

        Path zip = targetDir.resolve(worldName + "_" + STAMP.format(now) + ".zip");
        Path partial = zip.resolveSibling(zip.getFileName() + ".part");
        try {
            try (OutputStream out = Files.newOutputStream(partial); ZipOutputStream zipOut = new ZipOutputStream(out)) {
                writeTree(worldDir, worldName, zipOut);
            }
            Files.move(partial, zip);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        prune(targetDir, worldName, keep);
        return zip;
    }

    private static void writeTree(Path worldDir, String rootName, ZipOutputStream zipOut) throws IOException {
        Files.walkFileTree(worldDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = entryName(rootName, worldDir.relativize(dir)) + "/";
                zipOut.putNextEntry(new ZipEntry(name));
                zipOut.closeEntry();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString().equals(SESSION_LOCK)) {
                    return FileVisitResult.CONTINUE;
                }
                ZipEntry entry = new ZipEntry(entryName(rootName, worldDir.relativize(file)));
                entry.setLastModifiedTime(attrs.lastModifiedTime());
                zipOut.putNextEntry(entry);
                Files.copy(file, zipOut);
                zipOut.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String entryName(String rootName, Path relative) {
        String rel = relative.toString().replace('\\', '/');
        return rel.isEmpty() ? rootName : rootName + "/" + rel;
    }

    private static long sizeOf(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> {
                try {
                    return Files.size(file);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
        }
    }

    /** Deletes all but the newest {@code keep} automatic backups of one world. */
    static void prune(Path targetDir, String worldName, int keep) throws IOException {
        List<Path> backups = backupFiles(targetDir, worldName);
        for (int i = 0; i < backups.size() - keep; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }

    /** The automatic backups of one world, oldest first. The timestamp is fixed-width, so name order is age order. */
    private static List<Path> backupFiles(Path targetDir, String worldName) throws IOException {
        String prefix = worldName + "_";
        List<Path> backups = new ArrayList<>();
        if (!Files.isDirectory(targetDir)) {
            return backups;
        }
        try (Stream<Path> files = Files.list(targetDir)) {
            files.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".zip")
                        && name.length() == prefix.length() + "yyyy-MM-dd_HH-mm-ss".length() + ".zip".length();
            }).forEach(backups::add);
        }
        backups.sort(Comparator.comparing(file -> file.getFileName().toString()));
        return backups;
    }

    /** The automatic backups of a world, newest first. */
    public static List<Backup> list(Path gameDir, String worldName) throws IOException {
        List<Path> files = backupFiles(backupsDir(gameDir), worldName);
        List<Backup> backups = new ArrayList<>();
        for (int i = files.size() - 1; i >= 0; i--) {
            Path file = files.get(i);
            String name = file.getFileName().toString();
            String stamp = name.substring(worldName.length() + 1, name.length() - ".zip".length());
            try {
                backups.add(new Backup(file, LocalDateTime.parse(stamp, STAMP), Files.size(file)));
            } catch (java.time.format.DateTimeParseException e) {
                // Not one of ours after all.
            }
        }
        return backups;
    }

    /**
     * Replaces a world with the contents of a backup. The backup is unpacked next to the world first;
     * only when that worked is the world folder renamed aside and the unpacked copy put in its place.
     * The world must not be open.
     *
     * @return where the replaced world now is
     * @throws IOException if the backup cannot be read or the folders cannot be swapped; the world is then as it was
     */
    public static Path restore(Path backup, Path worldDir, LocalDateTime now) throws IOException {
        Path parent = worldDir.toAbsolutePath().normalize().getParent();
        String worldName = worldDir.getFileName().toString();
        Path unpacked = parent.resolve(worldName + ".restoring");
        Path aside = parent.resolve(worldName + "_before_restore_" + STAMP.format(now));
        deleteTree(unpacked);
        try {
            unzip(backup, unpacked);
            if (!Files.isRegularFile(unpacked.resolve("level.dat"))) {
                throw new IOException("Backup " + backup.getFileName() + " holds no world (no level.dat)");
            }
            if (Files.exists(worldDir)) {
                Files.move(worldDir, aside);
            }
            try {
                Files.move(unpacked, worldDir);
            } catch (IOException e) {
                if (Files.exists(aside)) {
                    Files.move(aside, worldDir);
                }
                throw e;
            }
        } finally {
            deleteTree(unpacked);
        }
        return aside;
    }

    /** Unpacks a backup whose entries all sit under one folder (the world's name) into {@code target}. */
    private static void unzip(Path zip, Path target) throws IOException {
        Files.createDirectories(target);
        Path root = target.toAbsolutePath().normalize();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(zip))) {
            for (java.util.zip.ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                String name = entry.getName().replace('\\', '/');
                int slash = name.indexOf('/');
                String inside = slash < 0 ? "" : name.substring(slash + 1);
                if (inside.isEmpty()) {
                    continue;
                }
                Path out = root.resolve(inside).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Backup entry " + entry.getName() + " points outside the world");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out);
                }
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(file);
            }
        }
    }

    /** Asks for {@code backup} to be restored into the world folder {@code world} the next time it is about to load. */
    public static void requestRestore(Path gameDir, String world, String backup) throws IOException {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("world", world);
        props.setProperty("backup", backup);
        Path file = backupsDir(gameDir).resolve(PENDING_FILE);
        Files.createDirectories(file.getParent());
        try (java.io.Writer out = Files.newBufferedWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
            props.store(out, "Worldsure restores this backup when the world is opened next");
        }
    }

    /** The restore asked for, if any. */
    public static java.util.Optional<PendingRestore> pendingRestore(Path gameDir) throws IOException {
        Path file = backupsDir(gameDir).resolve(PENDING_FILE);
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        java.util.Properties props = new java.util.Properties();
        try (java.io.Reader in = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            props.load(in);
        }
        String world = props.getProperty("world", "");
        String backup = props.getProperty("backup", "");
        return world.isEmpty() || backup.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.of(new PendingRestore(world, backup));
    }

    public static void clearPendingRestore(Path gameDir) throws IOException {
        Files.deleteIfExists(backupsDir(gameDir).resolve(PENDING_FILE));
    }
}
