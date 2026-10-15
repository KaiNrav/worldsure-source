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

/** Zips a world folder into {@code <game>/backups/worldsure/} and prunes old copies. */
public final class WorldBackup {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
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
        // The timestamp is fixed-width, so name order is age order.
        String prefix = worldName + "_";
        List<Path> backups = new ArrayList<>();
        try (Stream<Path> files = Files.list(targetDir)) {
            files.filter(file -> {
                String name = file.getFileName().toString();
                return name.startsWith(prefix) && name.endsWith(".zip")
                        && name.length() == prefix.length() + "yyyy-MM-dd_HH-mm-ss".length() + ".zip".length();
            }).forEach(backups::add);
        }
        backups.sort(Comparator.comparing(file -> file.getFileName().toString()));
        for (int i = 0; i < backups.size() - keep; i++) {
            Files.deleteIfExists(backups.get(i));
        }
    }
}
