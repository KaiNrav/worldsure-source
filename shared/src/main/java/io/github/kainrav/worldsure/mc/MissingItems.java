package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
//#if >=1.21.11
import net.minecraft.resources.Identifier;
//#else
//$ import net.minecraft.resources.ResourceLocation;
//#endif
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps items of removed mods. Before the game reads saved data (a chest, a mob, a player's
 * inventory), every item stack whose item it no longer knows becomes a {@code worldsure:missing_item}
 * that carries the original stack inside it, so it can be moved, stored and dropped like any item.
 * Whenever that data is saved, each placeholder is turned back into the stack it carries, so files on
 * disk never hold a placeholder. Putting the mod back brings the items back wherever they were moved.
 *
 * <p>A placeholder is a single item that does not stack, so two kept stacks never merge into one
 * that could only carry one of them. Its tooltip names the item and how many it holds.
 */
public final class MissingItems {
    //#if >=1.21.11
    public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Worldsure.MOD_ID, "missing_item"));
    //#elif >=1.21
    //$ public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Worldsure.MOD_ID, "missing_item"));
    //#else
    //$ public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, new ResourceLocation(Worldsure.MOD_ID, "missing_item"));
    //#endif
    public static final String PLACEHOLDER_ID = Worldsure.MOD_ID + ":missing_item";
    /** The key under the placeholder's own data that holds the original stack. */
    static final String ORIGINAL = "worldsure_original";

    //#if >=1.20.5
    private static final String COUNT = "count";
    private static final String DATA = "components";
    private static final String CUSTOM_DATA = "minecraft:custom_data";
    //#else
    //$ private static final String COUNT = "Count";
    //$ private static final String DATA = "tag";
    //#endif

    /** Set once a placeholder exists, so saves before that skip looking for them. */
    private static volatile boolean active;

    private MissingItems() {
    }

    public static Item create() {
        return new MissingItem(new Item.Properties()
                //#if >=1.21.2
                .setId(KEY)
                //#endif
                .stacksTo(1));
    }

    /** Whether a compound is shaped like a saved item stack: an id and a count, and nothing that marks an entity or block entity. */
    static boolean isStack(CompoundTag tag) {
        return tag.get("id") instanceof StringTag
                && (tag.get("count") instanceof NumericTag || tag.get("Count") instanceof NumericTag)
                && !tag.contains("Pos") && !tag.contains("UUID") && !tag.contains("x");
    }

    private static boolean isUnknownItem(String id) {
        return MissingBlocks.isUnknown(id, BuiltInRegistries.ITEM);
    }

    private static boolean isPlaceholder(CompoundTag stack) {
        return PLACEHOLDER_ID.equals(Nbt.stringOrEmpty(stack, "id"));
    }

    /** The stack a placeholder carries, or null if it carries none (one made by a command, say). */
    static CompoundTag original(CompoundTag placeholder) {
        CompoundTag data = Nbt.compound(placeholder, DATA).orElse(null);
        //#if >=1.20.5
        if (data != null) {
            data = Nbt.compound(data, CUSTOM_DATA).orElse(null);
        }
        //#endif
        return data == null ? null : Nbt.compound(data, ORIGINAL).orElse(null);
    }

    /** A placeholder carrying a copy of {@code stack}, in the slot the stack was in. */
    static CompoundTag placeholderFor(CompoundTag stack) {
        CompoundTag original = stack.copy();
        CompoundTag placeholder = new CompoundTag();
        placeholder.putString("id", PLACEHOLDER_ID);
        //#if >=1.20.5
        placeholder.putInt(COUNT, 1);
        CompoundTag custom = new CompoundTag();
        custom.put(ORIGINAL, original);
        CompoundTag components = new CompoundTag();
        components.put(CUSTOM_DATA, custom);
        placeholder.put(DATA, components);
        //#else
        //$ placeholder.putByte(COUNT, (byte) 1);
        //$ CompoundTag data = new CompoundTag();
        //$ data.put(ORIGINAL, original);
        //$ placeholder.put(DATA, data);
        //#endif
        copySlot(stack, placeholder);
        return placeholder;
    }

    /** Containers save the slot inside the stack; the placeholder may have moved, so its slot wins. */
    private static void copySlot(CompoundTag from, CompoundTag to) {
        for (String key : new String[]{"Slot", "slot"}) {
            Tag slot = from.get(key);
            if (slot != null) {
                to.put(key, slot.copy());
            } else {
                to.remove(key);
            }
        }
    }

    /**
     * Saved data about to be read: unknown item stacks become placeholders, and placeholders whose item
     * is known again (the mod is back) become their item. Nothing is changed in place.
     *
     * @return the tag to read: the same one when nothing changed, otherwise a changed copy
     */
    public static CompoundTag wrap(CompoundTag tag) {
        if (tag == null) {
            return null;
        }
        int[] made = new int[1];
        Tag out = wrap(tag, made);
        if (made[0] > 0) {
            active = true;
        }
        return (CompoundTag) out;
    }

    private static Tag wrap(Tag tag, int[] made) {
        if (tag instanceof CompoundTag compound) {
            if (isStack(compound)) {
                String id = Nbt.stringOrEmpty(compound, "id");
                if (isUnknownItem(id)) {
                    made[0]++;
                    return placeholderFor(compound);
                }
                if (PLACEHOLDER_ID.equals(id)) {
                    CompoundTag original = original(compound);
                    if (original == null || isUnknownItem(Nbt.stringOrEmpty(original, "id"))) {
                        made[0]++;
                        return compound;
                    }
                    CompoundTag back = original.copy();
                    copySlot(compound, back);
                    return wrap(back, made);
                }
            }
            CompoundTag copy = null;
            for (String key : keys(compound)) {
                Tag child = compound.get(key);
                Tag changed = wrap(child, made);
                if (changed != child) {
                    if (copy == null) {
                        copy = shallowCopy(compound);
                    }
                    copy.put(key, changed);
                }
            }
            return copy == null ? compound : copy;
        }
        if (tag instanceof ListTag list) {
            ListTag copy = null;
            for (int i = 0; i < list.size(); i++) {
                Tag child = list.get(i);
                Tag changed = wrap(child, made);
                if (changed != child) {
                    if (copy == null) {
                        copy = list.copy();
                    }
                    copy.set(i, changed);
                }
            }
            return copy == null ? list : copy;
        }
        return tag;
    }

    /**
     * Saved data about to be written: every placeholder becomes the stack it carries, in place.
     *
     * @return the same tag
     */
    public static CompoundTag unwrap(CompoundTag tag) {
        if (active && tag != null) {
            if (isPlaceholder(tag)) {
                CompoundTag original = original(tag);
                if (original != null) {
                    replaceContents(tag, original);
                }
            } else {
                unwrapChildren(tag);
            }
        }
        return tag;
    }

    private static void unwrapChildren(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            for (String key : keys(compound)) {
                Tag child = compound.get(key);
                CompoundTag back = carried(child);
                if (back != null) {
                    compound.put(key, back);
                } else {
                    unwrapChildren(child);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                Tag child = list.get(i);
                CompoundTag back = carried(child);
                if (back != null) {
                    list.set(i, back);
                } else {
                    unwrapChildren(child);
                }
            }
        }
    }

    /** The stack a placeholder tag carries, in the placeholder's slot; null for anything else. */
    private static CompoundTag carried(Tag tag) {
        if (tag instanceof CompoundTag compound && isPlaceholder(compound) && isStack(compound)) {
            CompoundTag original = original(compound);
            if (original != null) {
                CompoundTag back = original.copy();
                copySlot(compound, back);
                return back;
            }
        }
        return null;
    }

    private static void replaceContents(CompoundTag target, CompoundTag original) {
        CompoundTag back = original.copy();
        copySlot(target, back);
        for (String key : keys(target)) {
            target.remove(key);
        }
        for (String key : keys(back)) {
            target.put(key, back.get(key));
        }
    }

    /**
     * Items per namespace in saved data, by count: in containers, on the ground, carried, nested in
     * shulker boxes and bundles. A placeholder counts as the item it carries.
     */
    public static Map<String, Long> count(Tag tag) {
        Map<String, Long> counts = new HashMap<>();
        countInto(tag, counts);
        return counts;
    }

    private static void countInto(Tag tag, Map<String, Long> counts) {
        if (tag instanceof CompoundTag compound) {
            if (isStack(compound)) {
                CompoundTag original = isPlaceholder(compound) ? original(compound) : null;
                CompoundTag stack = original != null ? original : compound;
                counts.merge(MissingBlocks.namespace(Nbt.stringOrEmpty(stack, "id")), stackSize(stack), Long::sum);
                countChildren(stack, counts);
                return;
            }
            countChildren(compound, counts);
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                countInto(list.get(i), counts);
            }
        }
    }

    /** How many items a saved stack holds; at least one. */
    static long stackSize(CompoundTag stack) {
        Tag count = stack.get("count") != null ? stack.get("count") : stack.get("Count");
        if (count instanceof NumericTag number) {
            //#if >=1.21.5
            return Math.max(1, number.longValue());
            //#else
            //$ return Math.max(1, number.getAsLong());
            //#endif
        }
        return 1;
    }

    private static void countChildren(CompoundTag compound, Map<String, Long> counts) {
        for (String key : keys(compound)) {
            if (!key.equals("id")) {
                countInto(compound.get(key), counts);
            }
        }
    }

    private static List<String> keys(CompoundTag tag) {
        //#if >=1.21.5
        return new ArrayList<>(tag.keySet());
        //#else
        //$ return new ArrayList<>(tag.getAllKeys());
        //#endif
    }

    private static CompoundTag shallowCopy(CompoundTag tag) {
        CompoundTag copy = new CompoundTag();
        for (String key : keys(tag)) {
            copy.put(key, tag.get(key));
        }
        return copy;
    }

    public static void clear() {
        active = false;
    }
}
