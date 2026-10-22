package io.github.kainrav.worldsure.mc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//#if >=1.21.5
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
//#else
//$ import net.minecraft.world.entity.EntityType;
//#endif

import java.util.List;

/** Game tests for {@link MissingItems}: placeholders made from saved data and turned back when it is saved. */
public final class ItemGameTests {
    private static final String GEM = "fakemod:gem";

    private ItemGameTests() {
    }

    /**
     * In a chest's saved data, a stack of a removed mod becomes a placeholder, also inside a shulker box;
     * a placeholder moved to another slot turns back into its stack there.
     */
    public static void placeholdersCarryTheirItems(GameTestHelper helper) {
        CompoundTag gem = stack(GEM, 5);
        putData(gem, "fakemod_charge", 3);
        gem.putByte("Slot", (byte) 0);
        CompoundTag stone = stack("minecraft:stone", 2);
        stone.putByte("Slot", (byte) 1);
        CompoundTag box = stack("minecraft:shulker_box", 1);
        box.putByte("Slot", (byte) 2);
        putContents(box, stack(GEM, 7));
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 1);
        chest.putInt("y", 2);
        chest.putInt("z", 3);
        chest.put("Items", EntityGameTests.list(gem, stone, box));

        CompoundTag loaded = MissingItems.wrap(chest);
        helper.assertTrue(loaded != chest, "the missing item was not noticed");
        helper.assertTrue(itemIds(chest).equals(List.of(GEM, "minecraft:stone", "minecraft:shulker_box")), "the saved data was changed");
        CompoundTag slot0 = Nbt.compoundAt(Nbt.list(loaded, "Items"), 0);
        helper.assertTrue(Nbt.stringOrEmpty(slot0, "id").equals(MissingItems.PLACEHOLDER_ID), "slot 0 holds " + slot0);
        helper.assertTrue(Nbt.byteOr(slot0, "Slot", (byte) -1) == 0, "the placeholder left its slot");
        helper.assertTrue(MissingItems.wrap(loaded) == loaded, "a placeholder was wrapped twice");
        String nested = Nbt.compoundAt(Nbt.list(loaded, "Items"), 2).toString();
        helper.assertTrue(nested.contains(MissingItems.PLACEHOLDER_ID), "the gem in the shulker box was not kept: " + nested);
        helper.assertTrue(MissingItems.count(loaded).get("fakemod") == 12, "counted " + MissingItems.count(loaded));

        // The player moves the placeholder to slot 5, then the chest is saved.
        CompoundTag saved = loaded.copy();
        Nbt.compoundAt(Nbt.list(saved, "Items"), 0).putByte("Slot", (byte) 5);
        MissingItems.unwrap(saved);
        CompoundTag back = Nbt.compoundAt(Nbt.list(saved, "Items"), 0);
        helper.assertTrue(Nbt.stringOrEmpty(back, "id").equals(GEM), "slot 0 was saved as " + back);
        helper.assertTrue(MissingItems.stackSize(back) == 5, "the gem stack lost items: " + back);
        helper.assertTrue(Nbt.byteOr(back, "Slot", (byte) -1) == 5, "the gem was saved in its old slot: " + back);
        helper.assertTrue(back.toString().contains("fakemod_charge"), "the gem lost its data: " + back);
        helper.assertTrue(!saved.toString().contains(MissingItems.PLACEHOLDER_ID), "a placeholder reached the save: " + saved);
        helper.succeed();
    }

    /** A real chest loaded from saved data holds a placeholder, and saving the chest gives the stack back. */
    public static void chestKeepsMissingItems(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState state = Blocks.CHEST.defaultBlockState();
        CompoundTag gem = stack(GEM, 5);
        gem.putByte("Slot", (byte) 0);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:chest");
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        tag.put("Items", EntityGameTests.list(gem));

        //#if >=1.20.5
        BlockEntity chest = BlockEntity.loadStatic(pos, state, tag, helper.getLevel().registryAccess());
        //#else
        //$ BlockEntity chest = BlockEntity.loadStatic(pos, state, tag);
        //#endif
        helper.assertTrue(chest instanceof Container, "the chest did not load: " + chest);
        ItemStack held = ((Container) chest).getItem(0);
        helper.assertTrue(held.is(placeholder()), "slot 0 holds " + held + " instead of a placeholder");
        CompoundTag original = MissingItem.originalOf(held);
        helper.assertTrue(original != null && Nbt.stringOrEmpty(original, "id").equals(GEM), "the placeholder carries " + original);

        //#if >=1.20.5
        CompoundTag saved = chest.saveWithFullMetadata(helper.getLevel().registryAccess());
        //#else
        //$ CompoundTag saved = chest.saveWithFullMetadata();
        //#endif
        CompoundTag back = Nbt.compoundAt(Nbt.list(saved, "Items"), 0);
        helper.assertTrue(Nbt.stringOrEmpty(back, "id").equals(GEM) && MissingItems.stackSize(back) == 5, "the chest saved " + back);
        helper.succeed();
    }

    /** A dropped item of a removed mod loads as a dropped placeholder and is saved as the item again. */
    public static void droppedItemKeepsMissingItem(GameTestHelper helper) {
        CompoundTag tag = EntityGameTests.entity("minecraft:item");
        tag.put("Item", stack(GEM, 9));
        //#if >=1.21.5
        Entity entity = EntityType.create(BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("item")), TagValueInput.create(ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(), tag), helper.getLevel(), EntitySpawnReason.LOAD).orElse(null);
        //#else
        //$ Entity entity = EntityType.create(tag, helper.getLevel()).orElse(null);
        //#endif
        helper.assertTrue(entity instanceof ItemEntity, "the dropped item did not load: " + entity);
        ItemStack held = ((ItemEntity) entity).getItem();
        helper.assertTrue(held.is(placeholder()), "the dropped item is " + held);

        //#if >=1.21.5
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        entity.saveWithoutId(output);
        CompoundTag saved = output.buildResult();
        //#else
        //$ CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        //#endif
        CompoundTag item = Nbt.compound(saved, "Item").orElse(new CompoundTag());
        helper.assertTrue(Nbt.stringOrEmpty(item, "id").equals(GEM) && MissingItems.stackSize(item) == 9, "the dropped item saved as " + item);
        helper.succeed();
    }

    private static Item placeholder() {
        return BuiltInRegistries.ITEM.getOptional(MissingItems.KEY).orElseThrow();
    }

    /** A saved stack as this version writes it. */
    private static CompoundTag stack(String id, int count) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", id);
        //#if >=1.20.5
        stack.putInt("count", count);
        //#else
        //$ stack.putByte("Count", (byte) count);
        //#endif
        return stack;
    }

    /** Data only the removed mod understands, where this version keeps a stack's extra data. */
    private static void putData(CompoundTag stack, String key, int value) {
        CompoundTag data = new CompoundTag();
        data.putInt(key, value);
        //#if >=1.20.5
        CompoundTag components = new CompoundTag();
        components.put("minecraft:custom_data", data);
        stack.put("components", components);
        //#else
        //$ stack.put("tag", data);
        //#endif
    }

    /** Puts one stack into a shulker box stack, the way this version stores its contents. */
    private static void putContents(CompoundTag box, CompoundTag content) {
        //#if >=1.20.5
        CompoundTag slot = new CompoundTag();
        slot.putInt("slot", 0);
        slot.put("item", content);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:container", EntityGameTests.list(slot));
        box.put("components", components);
        //#else
        //$ content.putByte("Slot", (byte) 0);
        //$ CompoundTag blockEntity = new CompoundTag();
        //$ blockEntity.put("Items", EntityGameTests.list(content));
        //$ CompoundTag tag = new CompoundTag();
        //$ tag.put("BlockEntityTag", blockEntity);
        //$ box.put("tag", tag);
        //#endif
    }

    private static List<String> itemIds(CompoundTag container) {
        ListTag items = Nbt.list(container, "Items");
        return Nbt.compounds(items).map(tag -> Nbt.stringOrEmpty(tag, "id")).toList();
    }
}
