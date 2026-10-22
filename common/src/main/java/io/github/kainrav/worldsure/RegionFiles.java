package io.github.kainrav.worldsure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads the chunks stored in region files ({@code r.<x>.<z>.mca}) without the game: a 4 KiB table
 * of where each of the 32x32 chunks sits, then each chunk as a length, a compression byte and the
 * compressed NBT. Chunks too large for the file live next to it in {@code c.<x>.<z>.mcc}.
 */
public final class RegionFiles {
    private static final int SECTOR = 4096;
    private static final Pattern NAME = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");
    private static final int GZIP = 1;
    private static final int ZLIB = 2;
    private static final int NONE = 3;
    private static final int EXTERNAL = 128;

    private RegionFiles() {
    }

    /** One stored chunk: its absolute position and its uncompressed NBT. */
    public record StoredChunk(int x, int z, InputStream nbt) {
    }

    /** Decompresses the methods the JDK does not know (LZ4 since 1.20.5); null when unknown. */
    public interface Decompressor {
        InputStream open(int method, InputStream compressed) throws IOException;
    }

    /** The region files in a folder, empty when there is no such folder. */
    public static List<Path> list(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> NAME.matcher(file.getFileName().toString()).matches()).sorted().toList();
        }
    }

    /**
     * Every chunk stored in a region file. A chunk that cannot be read (half written while the game
     * saves, an unknown compression) is left out rather than failing the whole file.
     */
    public static List<StoredChunk> read(Path regionFile, Decompressor other) throws IOException {
        Matcher name = NAME.matcher(regionFile.getFileName().toString());
        if (!name.matches()) {
            throw new IOException("Not a region file: " + regionFile);
        }
        int regionX = Integer.parseInt(name.group(1));
        int regionZ = Integer.parseInt(name.group(2));
        List<StoredChunk> chunks = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            if (file.length() < SECTOR) {
                return chunks;
            }
            byte[] header = new byte[SECTOR];
            file.readFully(header);
            for (int i = 0; i < 1024; i++) {
                int entry = ((header[i * 4] & 0xFF) << 24) | ((header[i * 4 + 1] & 0xFF) << 16)
                        | ((header[i * 4 + 2] & 0xFF) << 8) | (header[i * 4 + 3] & 0xFF);
                int offset = entry >>> 8;
                int sectors = entry & 0xFF;
                if (offset < 2 || sectors == 0 || (long) (offset + sectors) * SECTOR > file.length()) {
                    continue;
                }
                int x = regionX * 32 + (i & 31);
                int z = regionZ * 32 + (i >> 5);
                try {
                    file.seek((long) offset * SECTOR);
                    int length = file.readInt();
                    int method = file.readUnsignedByte();
                    if (length < 1 || length > sectors * SECTOR) {
                        continue;
                    }
                    InputStream compressed;
                    if ((method & EXTERNAL) != 0) {
                        Path external = regionFile.resolveSibling("c." + x + "." + z + ".mcc");
                        if (!Files.isRegularFile(external)) {
                            continue;
                        }
                        compressed = new ByteArrayInputStream(Files.readAllBytes(external));
                        method &= ~EXTERNAL;
                    } else {
                        byte[] data = new byte[length - 1];
                        file.readFully(data);
                        compressed = new ByteArrayInputStream(data);
                    }
                    InputStream nbt = switch (method) {
                        case GZIP -> new GZIPInputStream(compressed);
                        case ZLIB -> new InflaterInputStream(compressed);
                        case NONE -> compressed;
                        default -> other.open(method, compressed);
                    };
                    if (nbt != null) {
                        chunks.add(new StoredChunk(x, z, nbt));
                    }
                } catch (IOException e) {
                    // A chunk being rewritten right now; the game counts it when it saves it.
                }
            }
        }
        return chunks;
    }
}
