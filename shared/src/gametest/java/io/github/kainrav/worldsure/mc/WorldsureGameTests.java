package io.github.kainrav.worldsure.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.github.kainrav.worldsure.ModInfo;
import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.WorldManifest;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
//#if >=26.3
import net.minecraft.nbt.StringTag;
//#endif
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Checks that run inside a real game test server on every loader and Minecraft version, so they
 * see the real registries, the loader's server lifecycle and the world the server just created.
 * Each loader registers them its own way, from its gametest sources; none of this ships in the jars.
 */
public final class WorldsureGameTests {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String PLACEHOLDER = Worldsure.MOD_ID + ":missing_block";
    private static final String MISSING = "fakemod:stone";
    private static final String MISSING_MACHINE = "fakemod:machine";

    private WorldsureGameTests() {
    }

    /** Every check by name, for loaders that register tests one by one. */
    public static Map<String, Consumer<GameTestHelper>> all() {
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("passport_records_its_format", WorldsureGameTests::passportRecordsItsFormat);
        tests.put("placeholder_cannot_be_broken", WorldsureGameTests::placeholderCannotBeBroken);
        tests.put("missing_blocks_come_back", WorldsureGameTests::missingBlocksComeBack);
        return tests;
    }

    /** The world the test server created got a passport of the current format, listing the running mods. */
    public static void passportRecordsItsFormat(GameTestHelper helper) {
        Path world = WorldsureMc.worldDir(helper.getLevel().getServer());
        Path file = WorldManifest.path(world);
        helper.assertTrue(Files.isRegularFile(file), "no passport at " + file);
        JsonElement format;
        WorldManifest passport;
        try {
            format = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get("format");
            passport = WorldManifest.read(world).orElseThrow();
        } catch (IOException | RuntimeException e) {
            helper.fail("could not read the passport: " + e);
            return;
        }
        helper.assertTrue(format != null && format.getAsInt() == WorldManifest.FORMAT,
                "the passport on disk has format " + format + ", expected " + WorldManifest.FORMAT);
        helper.assertTrue(passport.format() == WorldManifest.FORMAT && !passport.isNewer(),
                "the passport reads as format " + passport.format());
        List<String> ids = passport.mods().stream().map(ModInfo::id).toList();
        helper.assertTrue(ids.contains(Worldsure.MOD_ID) && ids.contains("minecraft"),
                "the passport lists " + ids + " without worldsure or minecraft");
        helper.succeed();
    }

    /** The placeholder block is registered and survives anything short of creative mode. */
    public static void placeholderCannotBeBroken(GameTestHelper helper) {
        Block block = BuiltInRegistries.BLOCK.getOptional(MissingBlocks.KEY).orElse(null);
        helper.assertTrue(block != null, PLACEHOLDER + " is not registered");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, block);
        BlockState state = helper.getBlockState(pos);
        helper.assertTrue(state.is(block), "placed " + state + " instead of the placeholder");
        helper.assertTrue(state.getDestroySpeed(helper.getLevel(), helper.absolutePos(pos)) < 0, "the placeholder can be mined");
        helper.assertTrue(block.getExplosionResistance() >= 3_600_000.0F, "the placeholder can be blown up");
        helper.succeed();
    }

    /**
     * A chunk holding a block and a block entity of a mod that is not installed gets placeholders on the
     * way in and its own blocks back on the way out. What counts as missing is judged by the game's registries.
     */
    public static void missingBlocksComeBack(GameTestHelper helper) {
        List<Tag> entries = new ArrayList<>(PackedSection.SIZE);
        for (int i = 0; i < PackedSection.SIZE; i++) {
            entries.add(paletteEntry(i % 7 == 0 ? MISSING : i % 2 == 0 ? "minecraft:stone" : "minecraft:air"));
        }
        PackedSection<Tag> original = PackedSection.of(entries);
        // Far from the test area, so the game never saves a real chunk there while the stash is held.
        CompoundTag chunk = chunkTag(30_000, -30_000, original);

        CompoundTag loaded = MissingBlocks.replaceMissing(DIMENSION, chunk);
        helper.assertTrue(loaded != null, "the missing block was not noticed");
        List<String> loadedIds = paletteIds(loaded);
        helper.assertTrue(!loadedIds.contains(MISSING) && loadedIds.contains(PLACEHOLDER), "the palette after loading is " + loadedIds);
        helper.assertTrue(blockEntityIds(loaded).isEmpty(), "the unknown block entity was loaded");

        CompoundTag saved = loaded.copy();
        MissingBlocks.restore(DIMENSION, saved);
        List<String> savedIds = paletteIds(saved);
        helper.assertTrue(savedIds.contains(MISSING) && !savedIds.contains(PLACEHOLDER), "the palette after saving is " + savedIds);
        helper.assertTrue(blockEntityIds(saved).equals(List.of(MISSING_MACHINE)), "the block entities after saving are " + blockEntityIds(saved));
        long missing = original.countBy(MissingBlocks::blockId).get(MISSING);
        long[] counted = MissingBlocks.count(saved).get("fakemod");
        helper.assertTrue(counted != null && counted[0] == missing && counted[1] == 1,
                "the saved chunk counts " + (counted == null ? "nothing" : counted[0] + " blocks, " + counted[1] + " block entities")
                        + " of fakemod, expected " + missing + " and 1");

        // A save without placeholders (all of them broken) lets the stash go, so it does not linger.
        MissingBlocks.restore(DIMENSION, saved.copy());
        CompoundTag again = loaded.copy();
        MissingBlocks.restore(DIMENSION, again);
        helper.assertTrue(paletteIds(again).contains(PLACEHOLDER), "the stash outlived the placeholders");
        helper.succeed();
    }

    private static Tag paletteEntry(String id) {
        //#if >=26.3
        return StringTag.valueOf(id);
        //#else
        //$ CompoundTag entry = new CompoundTag();
        //$ entry.putString("Name", id);
        //$ return entry;
        //#endif
    }

    /** A chunk with one section at y 64 to 79 and a machine of the missing mod at its first block. */
    private static CompoundTag chunkTag(int x, int z, PackedSection<Tag> blocks) {
        CompoundTag blockStates = new CompoundTag();
        ListTag palette = new ListTag();
        palette.addAll(blocks.palette());
        blockStates.put("palette", palette);
        blockStates.put("data", new LongArrayTag(blocks.pack(MissingBlocks.globalBits())));
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 4);
        section.put("block_states", blockStates);
        ListTag sections = new ListTag();
        sections.add(section);

        CompoundTag machine = new CompoundTag();
        machine.putString("id", MISSING_MACHINE);
        machine.putInt("x", x * 16);
        machine.putInt("y", 64);
        machine.putInt("z", z * 16);
        machine.putInt("energy", 1234);
        ListTag blockEntities = new ListTag();
        blockEntities.add(machine);

        CompoundTag chunk = new CompoundTag();
        chunk.putInt("xPos", x);
        chunk.putInt("zPos", z);
        chunk.put("sections", sections);
        chunk.put("block_entities", blockEntities);
        return chunk;
    }

    private static List<String> paletteIds(CompoundTag chunk) {
        CompoundTag section = Nbt.compoundAt(Nbt.list(chunk, "sections"), 0);
        return Nbt.list(Nbt.compound(section, "block_states").orElseThrow(), "palette").stream()
                .map(MissingBlocks::blockId)
                .toList();
    }

    private static List<String> blockEntityIds(CompoundTag chunk) {
        return Nbt.compounds(Nbt.list(chunk, "block_entities")).map(tag -> Nbt.stringOrEmpty(tag, "id")).toList();
    }
}
