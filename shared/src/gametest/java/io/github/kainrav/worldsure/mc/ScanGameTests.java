package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.CountIndex;
import io.github.kainrav.worldsure.ObjectCounts;
import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.WorldScan;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.zip.DeflaterOutputStream;

/** Game tests for the biomes of removed mods and the background count of a whole world. */
public final class ScanGameTests {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String GROVE = "fakemod:moonlight_grove";

    private ScanGameTests() {
    }

    /**
     * A biome of a removed mod loads as plains, judged by the level's own biome registry, and comes back
     * when the chunk is saved, except where the biome was changed meanwhile.
     */
    public static void missingBiomesComeBack(GameTestHelper helper) {
        Predicate<String> unknown = WorldsureMc.unknownBiome(helper.getLevel());
        helper.assertTrue(unknown.test(GROVE) && !unknown.test("minecraft:desert"), "the biome registry check is wrong");

        List<Tag> spots = new ArrayList<>();
        for (int i = 0; i < PackedSection.BIOME_SIZE; i++) {
            spots.add(StringTag.valueOf(i % 3 == 0 ? GROVE : "minecraft:desert"));
        }
        PackedSection<Tag> original = PackedSection.of(spots);
        CompoundTag chunk = chunk(30_020, -30_020, biomes(original));

        CompoundTag loaded = MissingBlocks.replaceMissing(DIMENSION, chunk, unknown);
        helper.assertTrue(loaded != null, "the missing biome was not noticed");
        List<String> loadedBiomes = biomeIds(loaded);
        helper.assertTrue(!loadedBiomes.contains(GROVE) && loadedBiomes.contains("minecraft:plains"), "biomes after loading: " + loadedBiomes);

        // Spot 0 was turned into a jungle with /fillbiome; spot 3 is still the stand-in.
        CompoundTag saved = loaded.copy();
        CompoundTag savedBiomes = biomesOf(saved);
        PackedSection<Tag> edited = unpack(savedBiomes);
        List<Tag> editedSpots = new ArrayList<>();
        for (int i = 0; i < PackedSection.BIOME_SIZE; i++) {
            editedSpots.add(i == 0 ? StringTag.valueOf("minecraft:jungle") : edited.get(i));
        }
        writeBiomes(savedBiomes, PackedSection.of(editedSpots));
        MissingBlocks.restore(DIMENSION, saved);

        PackedSection<Tag> back = unpack(biomesOf(saved));
        helper.assertTrue(Nbt.value((StringTag) back.get(0)).equals("minecraft:jungle"), "the changed spot lost its change");
        helper.assertTrue(Nbt.value((StringTag) back.get(3)).equals(GROVE), "spot 3 did not get its biome back: " + back.get(3));
        helper.assertTrue(Nbt.value((StringTag) back.get(1)).equals("minecraft:desert"), "a known biome changed");
        helper.succeed();
    }

    /**
     * The background count reads a region file the game never loaded, in the world's real dimension
     * folder, and counts its blocks, block entities, items and entities per mod.
     */
    public static void scanCountsStoredChunks(GameTestHelper helper) {
        List<Tag> blocks = new ArrayList<>();
        for (int i = 0; i < PackedSection.SIZE; i++) {
            blocks.add(blockEntry(i < 100 ? "fakemod:ore" : "minecraft:stone"));
        }
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        CompoundTag blockStates = new CompoundTag();
        PackedSection<Tag> packed = PackedSection.of(blocks);
        ListTag palette = new ListTag();
        palette.addAll(packed.palette());
        blockStates.put("palette", palette);
        blockStates.put("data", new LongArrayTag(packed.pack(MissingBlocks.globalBits())));
        section.put("block_states", blockStates);
        CompoundTag machine = new CompoundTag();
        machine.putString("id", "fakemod:machine");
        machine.putInt("x", 0);
        machine.putInt("y", 0);
        machine.putInt("z", 0);
        CompoundTag gem = new CompoundTag();
        gem.putString("id", "fakemod:gem");
        gem.putInt("count", 7);
        gem.putByte("Count", (byte) 7);
        machine.put("Items", EntityGameTests.list(gem));
        CompoundTag chunk = NbtUtils.addCurrentDataVersion(chunk(16_000, 16_000, section));
        chunk.put("block_entities", EntityGameTests.list(machine));

        CompoundTag entities = NbtUtils.addCurrentDataVersion(new CompoundTag());
        entities.put("Position", new IntArrayTag(new int[]{16_000, 16_000}));
        entities.put("Entities", EntityGameTests.list(EntityGameTests.entity("fakemod:mob")));

        CountIndex index = new CountIndex();
        Path regionFile;
        Path entityFile;
        try {
            WorldScan.Dimension overworld = WorldScanMc.dimensions(helper.getLevel().getServer()).stream()
                    .filter(dimension -> dimension.id().equals(DIMENSION)).findFirst().orElseThrow();
            regionFile = overworld.regionFolder().resolve("r.500.500.mca");
            entityFile = overworld.entitiesFolder().resolve("r.500.500.mca");
            writeRegion(regionFile, chunk);
            writeRegion(entityFile, entities);
            WorldScanMc.create(helper.getLevel().getServer(), index).run();
            Files.deleteIfExists(regionFile);
            Files.deleteIfExists(entityFile);
        } catch (IOException | RuntimeException e) {
            helper.fail("the scan failed: " + e);
            return;
        }
        ObjectCounts fakemod = index.totals().get("fakemod");
        helper.assertTrue(fakemod != null, "the scan counted nothing of fakemod: " + index.totals());
        helper.assertTrue(fakemod.equals(new ObjectCounts(100, 1, 1, 7)), "the scan counted " + fakemod);
        helper.assertTrue(index.isScanned(DIMENSION), "the overworld was not marked as scanned");
        helper.succeed();
    }

    /** Writes a region file holding one chunk at local index 0, the way the game stores it (zlib). */
    private static void writeRegion(Path file, CompoundTag chunk) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(raw))) {
            NbtIo.write(chunk, out);
        }
        byte[] data = raw.toByteArray();
        Files.createDirectories(file.getParent());
        try (RandomAccessFile out = new RandomAccessFile(file.toFile(), "rw")) {
            int sectors = (data.length + 5 + 4095) / 4096;
            out.setLength((2L + sectors) * 4096);
            out.seek(0);
            out.writeInt((2 << 8) | sectors);
            out.seek(2 * 4096L);
            out.writeInt(data.length + 1);
            out.writeByte(2);
            out.write(data);
        }
    }

    private static Tag blockEntry(String id) {
        //#if >=26.3
        return StringTag.valueOf(id);
        //#else
        //$ CompoundTag entry = new CompoundTag();
        //$ entry.putString("Name", id);
        //$ return entry;
        //#endif
    }

    private static CompoundTag chunk(int x, int z, CompoundTag section) {
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("xPos", x);
        chunk.putInt("zPos", z);
        ListTag sections = new ListTag();
        sections.add(section);
        chunk.put("sections", sections);
        return chunk;
    }

    private static CompoundTag biomes(PackedSection<Tag> biomes) {
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 4);
        CompoundTag tag = new CompoundTag();
        writeBiomes(tag, biomes);
        section.put("biomes", tag);
        return section;
    }

    private static void writeBiomes(CompoundTag tag, PackedSection<Tag> biomes) {
        ListTag palette = new ListTag();
        palette.addAll(biomes.palette());
        tag.put("palette", palette);
        long[] data = biomes.packBiomes();
        if (data != null) {
            tag.put("data", new LongArrayTag(data));
        } else {
            tag.remove("data");
        }
    }

    private static CompoundTag biomesOf(CompoundTag chunk) {
        return Nbt.compound(Nbt.compoundAt(Nbt.list(chunk, "sections"), 0), "biomes").orElseThrow();
    }

    private static PackedSection<Tag> unpack(CompoundTag biomes) {
        return PackedSection.unpackBiomes(new ArrayList<>(Nbt.stringList(biomes, "palette")), Nbt.longArray(biomes, "data").orElse(null));
    }

    private static List<String> biomeIds(CompoundTag chunk) {
        return Nbt.stringList(biomesOf(chunk), "palette").stream().map(tag -> Nbt.value((StringTag) tag)).toList();
    }
}
