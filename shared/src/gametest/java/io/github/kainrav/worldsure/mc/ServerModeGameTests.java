package io.github.kainrav.worldsure.mc;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.WorldBackup;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
//#if >=26.3
import net.minecraft.nbt.StringTag;
//#endif
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Game tests for server-only stand-ins and the server commands. */
public final class ServerModeGameTests {
    private static final String DIMENSION = "minecraft:overworld";

    private ServerModeGameTests() {
    }

    /**
     * In server-only mode missing blocks load as bedrock, and on save only the spots that held a missing
     * block come back: real bedrock, and bedrock a player put on another spot, stay bedrock.
     */
    public static void serverOnlyBlocksUseBedrock(GameTestHelper helper) {
        List<Tag> entries = new ArrayList<>();
        for (int i = 0; i < PackedSection.SIZE; i++) {
            entries.add(entry(i % 5 == 0 ? "fakemod:stone" : i % 5 == 1 ? Placeholders.VANILLA_BLOCK : "minecraft:stone"));
        }
        CompoundTag chunk = chunk(30_040, -30_040, PackedSection.of(entries));
        Placeholders.setServerOnly(true);
        try {
            CompoundTag loaded = MissingBlocks.replaceMissing(DIMENSION, chunk);
            helper.assertTrue(loaded != null, "the missing block was not noticed");
            PackedSection<Tag> asLoaded = section(loaded);
            helper.assertTrue(MissingBlocks.blockId(asLoaded.get(0)).equals(Placeholders.VANILLA_BLOCK), "spot 0 loaded as " + asLoaded.get(0));

            // A player puts bedrock on spot 2, which held stone.
            List<Tag> edited = new ArrayList<>();
            for (int i = 0; i < PackedSection.SIZE; i++) {
                edited.add(i == 2 ? entry(Placeholders.VANILLA_BLOCK) : asLoaded.get(i));
            }
            CompoundTag saved = chunk(30_040, -30_040, PackedSection.of(edited));
            MissingBlocks.restore(DIMENSION, saved);
            PackedSection<Tag> back = section(saved);
            helper.assertTrue(MissingBlocks.blockId(back.get(0)).equals("fakemod:stone"), "spot 0 was saved as " + back.get(0));
            helper.assertTrue(MissingBlocks.blockId(back.get(1)).equals(Placeholders.VANILLA_BLOCK), "real bedrock was replaced: " + back.get(1));
            helper.assertTrue(MissingBlocks.blockId(back.get(2)).equals(Placeholders.VANILLA_BLOCK), "the player's bedrock was replaced: " + back.get(2));
            helper.assertTrue(MissingBlocks.blockId(back.get(3)).equals("minecraft:stone"), "stone changed: " + back.get(3));
            MissingBlocks.restore(DIMENSION, chunk(30_040, -30_040, PackedSection.of(edited.stream().map(t -> entry("minecraft:stone")).toList())));
        } finally {
            Placeholders.setServerOnly(false);
        }
        helper.succeed();
    }

    /** In server-only mode missing items become barriers named after them; two identical stacks never merge. */
    public static void serverOnlyItemsUseBarriers(GameTestHelper helper) {
        CompoundTag first = stack("fakemod:gem", 5, 0);
        CompoundTag second = stack("fakemod:gem", 5, 1);
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 0);
        chest.putInt("y", 0);
        chest.putInt("z", 0);
        chest.put("Items", EntityGameTests.list(first, second));
        Placeholders.setServerOnly(true);
        CompoundTag loaded;
        try {
            loaded = MissingItems.wrap(chest);
        } finally {
            Placeholders.setServerOnly(false);
        }
        ListTag items = Nbt.list(loaded, "Items");
        CompoundTag a = Nbt.compoundAt(items, 0);
        CompoundTag b = Nbt.compoundAt(items, 1);
        helper.assertTrue(Nbt.stringOrEmpty(a, "id").equals(Placeholders.VANILLA_ITEM), "slot 0 holds " + a);
        helper.assertTrue(a.toString().contains("fakemod:gem ×5"), "the barrier is not named after the gem: " + a);
        CompoundTag aWithoutSlot = a.copy();
        aWithoutSlot.remove("Slot");
        CompoundTag bWithoutSlot = b.copy();
        bWithoutSlot.remove("Slot");
        helper.assertTrue(!aWithoutSlot.equals(bWithoutSlot), "two identical kept stacks would merge");

        MissingItems.unwrap(loaded);
        List<String> back = Nbt.compounds(Nbt.list(loaded, "Items")).map(tag -> Nbt.stringOrEmpty(tag, "id") + "@" + Nbt.byteOr(tag, "Slot", (byte) -1)).toList();
        helper.assertTrue(back.equals(List.of("fakemod:gem@0", "fakemod:gem@1")), "the chest was saved with " + back);
        helper.succeed();
    }

    /** /worldsure status, backup and scan run on the server. */
    public static void statusBackupAndScanCommands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CommandSourceStack source = server.createCommandSourceStack();
        String world = WorldsureMc.worldDir(server).getFileName().toString();
        try {
            int before = WorldBackup.list(Platform.get().gameDir(), world).size();
            server.getCommands().getDispatcher().execute("worldsure status", source);
            server.getCommands().getDispatcher().execute("worldsure", source);
            int made = server.getCommands().getDispatcher().execute("worldsure backup", source);
            helper.assertTrue(made == 1, "/worldsure backup returned " + made);
            List<WorldBackup.Backup> backups = WorldBackup.list(Platform.get().gameDir(), world);
            helper.assertTrue(backups.size() == before + 1, "no new backup: " + backups);
            Files.deleteIfExists(backups.get(0).file());
            int scanned = server.getCommands().getDispatcher().execute("worldsure scan", source);
            helper.assertTrue(scanned == 1, "/worldsure scan returned " + scanned);
        } catch (CommandSyntaxException e) {
            helper.fail("a command failed to parse: " + e.getMessage());
            return;
        } catch (IOException e) {
            helper.fail("could not list the backups: " + e);
            return;
        }
        helper.succeed();
    }

    private static CompoundTag stack(String id, int count, int slot) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        //#if >=1.20.5
        stack.putInt("count", count);
        //#else
        //$ stack.putByte("Count", (byte) count);
        //#endif
        stack.putByte("Slot", (byte) slot);
        return stack;
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

    private static CompoundTag chunk(int x, int z, PackedSection<Tag> blocks) {
        CompoundTag blockStates = new CompoundTag();
        ListTag palette = new ListTag();
        palette.addAll(blocks.palette());
        blockStates.put("palette", palette);
        long[] data = blocks.pack(MissingBlocks.globalBits());
        if (data != null) {
            blockStates.put("data", new LongArrayTag(data));
        }
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 4);
        section.put("block_states", blockStates);
        ListTag sections = new ListTag();
        sections.add(section);
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("xPos", x);
        chunk.putInt("zPos", z);
        chunk.put("sections", sections);
        return chunk;
    }

    private static PackedSection<Tag> section(CompoundTag chunk) {
        CompoundTag blockStates = Nbt.compound(Nbt.compoundAt(Nbt.list(chunk, "sections"), 0), "block_states").orElseThrow();
        return PackedSection.unpack(new ArrayList<>(Nbt.list(blockStates, "palette")), Nbt.longArray(blockStates, "data").orElse(null),
                MissingBlocks.globalBits());
    }
}
