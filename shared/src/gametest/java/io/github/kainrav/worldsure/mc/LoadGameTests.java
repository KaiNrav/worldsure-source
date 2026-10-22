package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Load: many chunks, entities and items of removed mods going through Worldsure from several threads
 * at once, the way chunk saves run on worker threads. Nothing may be lost, nothing may linger in memory.
 */
public final class LoadGameTests {
    private static final String DIMENSION = "minecraft:overworld";
    private static final int CHUNKS = 256;
    private static final int THREADS = 8;

    private LoadGameTests() {
    }

    /** 256 chunks with blocks, block entities and biomes of a removed mod, read and written in parallel. */
    public static void parallelChunkSavesKeepEverything(GameTestHelper helper) {
        Predicate<String> unknownBiome = WorldsureMc.unknownBiome(helper.getLevel());
        int stashesBefore = MissingBlocks.stashCount();
        List<CompoundTag> chunks = new ArrayList<>();
        Random random = new Random(42);
        for (int i = 0; i < CHUNKS; i++) {
            chunks.add(chunk(40_000 + i, -40_000, random));
        }
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<String> problems = new ArrayList<>();
        try {
            List<Future<String>> results = new ArrayList<>();
            for (CompoundTag chunk : chunks) {
                results.add(pool.submit(() -> roundTrip(chunk, unknownBiome)));
            }
            for (Future<String> result : results) {
                String problem = result.get();
                if (problem != null) {
                    problems.add(problem);
                }
            }
        } catch (Exception e) {
            helper.fail("a chunk round trip threw " + e);
            return;
        } finally {
            pool.shutdownNow();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;
        Worldsure.LOG.info("Load test: {} chunks read and written on {} threads in {} ms", CHUNKS, THREADS, millis);
        helper.assertTrue(problems.isEmpty(), problems.size() + " chunks lost something, first: " + (problems.isEmpty() ? "" : problems.get(0)));
        helper.assertTrue(MissingBlocks.stashCount() == stashesBefore,
                "kept data lingers for " + (MissingBlocks.stashCount() - stashesBefore) + " chunks after their placeholders were gone");
        helper.assertTrue(millis < 60_000, "the load test took " + millis + " ms");
        helper.succeed();
    }

    /** Loads a chunk, saves it twice (an autosave and the final save), then saves it after the placeholders were broken. */
    private static String roundTrip(CompoundTag original, Predicate<String> unknownBiome) {
        CompoundTag loaded = MissingBlocks.replaceMissing(DIMENSION, original, unknownBiome);
        if (loaded == null) {
            return "chunk " + Nbt.intOr(original, "xPos", 0) + " was not noticed";
        }
        for (int save = 0; save < 2; save++) {
            CompoundTag saved = loaded.copy();
            MissingBlocks.restore(DIMENSION, saved);
            String problem = compare(original, saved);
            if (problem != null) {
                return "save " + save + " of chunk " + Nbt.intOr(original, "xPos", 0) + ": " + problem;
            }
        }
        // Every placeholder broken: the next save drops the kept data.
        CompoundTag cleared = loaded.copy();
        CompoundTag section = Nbt.compoundAt(Nbt.list(cleared, "sections"), 0);
        writeBlocks(Nbt.compound(section, "block_states").orElseThrow(),
                PackedSection.of(new ArrayList<>(java.util.Collections.nCopies(PackedSection.SIZE, entry("minecraft:air")))));
        CompoundTag biomes = Nbt.compound(section, "biomes").orElseThrow();
        ListTag plains = new ListTag();
        plains.add(StringTag.valueOf("minecraft:plains"));
        biomes.put("palette", plains);
        biomes.remove("data");
        section.remove("biomes");
        MissingBlocks.restore(DIMENSION, cleared);
        return null;
    }

    private static String compare(CompoundTag original, CompoundTag saved) {
        PackedSection<Tag> before = blocks(original);
        PackedSection<Tag> after = blocks(saved);
        for (int i = 0; i < PackedSection.SIZE; i++) {
            if (!MissingBlocks.blockId(before.get(i)).equals(MissingBlocks.blockId(after.get(i)))) {
                return "block " + i + " is " + MissingBlocks.blockId(after.get(i)) + ", was " + MissingBlocks.blockId(before.get(i));
            }
        }
        long machines = Nbt.compounds(Nbt.list(saved, "block_entities"))
                .filter(tag -> Nbt.stringOrEmpty(tag, "id").equals("fakemod:machine")).count();
        if (machines != 1) {
            return machines + " machines after saving";
        }
        List<String> biomes = Nbt.stringList(Nbt.compound(Nbt.compoundAt(Nbt.list(saved, "sections"), 0), "biomes").orElseThrow(), "palette")
                .stream().map(tag -> Nbt.value((StringTag) tag)).toList();
        if (!biomes.contains("fakemod:grove")) {
            return "biomes after saving are " + biomes;
        }
        return null;
    }

    private static CompoundTag chunk(int x, int z, Random random) {
        List<Tag> entries = new ArrayList<>(PackedSection.SIZE);
        for (int i = 0; i < PackedSection.SIZE; i++) {
            int roll = random.nextInt(10);
            entries.add(entry(i == 0 || roll == 0 ? "fakemod:ore" : roll == 1 ? "fakemod:pipe" : roll < 6 ? "minecraft:stone" : "minecraft:air"));
        }
        CompoundTag blockStates = new CompoundTag();
        writeBlocks(blockStates, PackedSection.of(entries));
        List<Tag> spots = new ArrayList<>();
        for (int i = 0; i < PackedSection.BIOME_SIZE; i++) {
            spots.add(StringTag.valueOf(random.nextBoolean() ? "fakemod:grove" : "minecraft:forest"));
        }
        spots.set(0, StringTag.valueOf("fakemod:grove"));
        PackedSection<Tag> biomeSection = PackedSection.of(spots);
        CompoundTag biomes = new CompoundTag();
        ListTag biomePalette = new ListTag();
        biomePalette.addAll(biomeSection.palette());
        biomes.put("palette", biomePalette);
        biomes.put("data", new LongArrayTag(biomeSection.packBiomes()));

        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 4);
        section.put("block_states", blockStates);
        section.put("biomes", biomes);
        ListTag sections = new ListTag();
        sections.add(section);

        CompoundTag machine = new CompoundTag();
        machine.putString("id", "fakemod:machine");
        machine.putInt("x", x * 16);
        machine.putInt("y", 64);
        machine.putInt("z", z * 16);
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("xPos", x);
        chunk.putInt("zPos", z);
        chunk.put("sections", sections);
        chunk.put("block_entities", EntityGameTests.list(machine));
        return chunk;
    }

    private static void writeBlocks(CompoundTag blockStates, PackedSection<Tag> blocks) {
        ListTag palette = new ListTag();
        palette.addAll(blocks.palette());
        blockStates.put("palette", palette);
        long[] data = blocks.pack(MissingBlocks.globalBits());
        if (data == null) {
            blockStates.remove("data");
        } else {
            blockStates.put("data", new LongArrayTag(data));
        }
    }

    private static PackedSection<Tag> blocks(CompoundTag chunk) {
        CompoundTag blockStates = Nbt.compound(Nbt.compoundAt(Nbt.list(chunk, "sections"), 0), "block_states").orElseThrow();
        return PackedSection.unpack(new ArrayList<>(Nbt.list(blockStates, "palette")), Nbt.longArray(blockStates, "data").orElse(null),
                MissingBlocks.globalBits());
    }

    private static Tag entry(String id) {
        //#if >=26.3
        return StringTag.valueOf(id);
        //#else
        //$ CompoundTag entry = new CompoundTag();
        //$ entry.putString("Name", id);
        //$ return entry;
        //#endif
    }
}
