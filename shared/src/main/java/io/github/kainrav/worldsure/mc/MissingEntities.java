package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps entities of removed mods. When the game reads a chunk's entities, every entity whose type
 * it no longer knows is taken out of the tag and kept in memory; the game would otherwise skip and
 * forget it. Whenever that chunk's entities are written, the kept entities are written along, so the
 * file on disk always holds them. Putting the mod back brings them back as they were.
 *
 * <p>An entity of a removed mod riding a known one (or carrying one) is kept whole when the entity
 * at the bottom is unknown; an unknown rider of a known entity is kept on its own and comes back
 * standing where it was, no longer riding.
 */
public final class MissingEntities {
    private static final String ENTITIES = "Entities";
    private static final String PASSENGERS = "Passengers";

    /** dimension + chunk -> entities of removed mods in that chunk, as they were read */
    private static final Map<String, List<CompoundTag>> STASHES = new ConcurrentHashMap<>();
    /** The region storages that load a running level's entities, with that level's dimension. */
    private static final Map<Object, String> STORAGES = Collections.synchronizedMap(new WeakHashMap<>());

    private MissingEntities() {
    }

    /** Remembers that a region storage loads the entities of a level, so its reads are cleaned. */
    public static void track(Object storage, String dimension) {
        STORAGES.put(storage, dimension);
    }

    /** The dimension whose entities a region storage loads, or null for any other storage (POI, world upgrades). */
    public static String dimensionOf(Object storage) {
        return STORAGES.get(storage);
    }

    private static String key(String dimension, int x, int z) {
        return dimension + "@" + MissingBlocks.chunkKey(x, z);
    }

    private static boolean isMissing(CompoundTag entity) {
        return MissingBlocks.isUnknown(Nbt.stringOrEmpty(entity, "id"), BuiltInRegistries.ENTITY_TYPE);
    }

    /**
     * Takes the entities of removed mods out of an entity chunk tag the game is about to load.
     *
     * @return the tag to load: the same tag when nothing is missing, otherwise a cleaned copy
     */
    public static CompoundTag keepMissing(String dimension, CompoundTag chunkTag) {
        ListTag entities = Nbt.list(chunkTag, ENTITIES);
        List<CompoundTag> kept = new ArrayList<>();
        ListTag known = new ListTag();
        boolean changed = false;
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = Nbt.compoundAt(entities, i);
            if (isMissing(entity)) {
                kept.add(entity.copy());
                changed = true;
                continue;
            }
            CompoundTag cleaned = withoutMissingRiders(entity, kept);
            changed |= cleaned != entity;
            known.add(cleaned);
        }
        if (!changed) {
            return chunkTag;
        }
        int[] pos = position(chunkTag);
        STASHES.put(key(dimension, pos[0], pos[1]), List.copyOf(kept));
        Worldsure.LOG.info("Kept {} entities of removed mods in chunk {}, {} of {}", kept.size(), pos[0], pos[1], dimension);
        CompoundTag copy = chunkTag.copy();
        copy.put(ENTITIES, known);
        return copy;
    }

    /** The entity without unknown riders at any depth, which go to {@code kept}; the same tag when it has none. */
    private static CompoundTag withoutMissingRiders(CompoundTag entity, List<CompoundTag> kept) {
        ListTag riders = Nbt.list(entity, PASSENGERS);
        if (riders.isEmpty()) {
            return entity;
        }
        ListTag remaining = new ListTag();
        boolean changed = false;
        for (int i = 0; i < riders.size(); i++) {
            CompoundTag rider = Nbt.compoundAt(riders, i);
            if (isMissing(rider)) {
                kept.add(rider.copy());
                changed = true;
            } else {
                CompoundTag cleaned = withoutMissingRiders(rider, kept);
                changed |= cleaned != rider;
                remaining.add(cleaned);
            }
        }
        if (!changed) {
            return entity;
        }
        CompoundTag copy = entity.copy();
        if (remaining.isEmpty()) {
            copy.remove(PASSENGERS);
        } else {
            copy.put(PASSENGERS, remaining);
        }
        return copy;
    }

    private static int[] position(CompoundTag chunkTag) {
        //#if >=1.21.5
        int[] pos = chunkTag.getIntArray("Position").orElse(new int[0]);
        //#else
        //$ int[] pos = chunkTag.getIntArray("Position");
        //#endif
        return pos.length >= 2 ? pos : new int[]{0, 0};
    }

    /**
     * Adds the kept entities to an entity chunk tag about to be written.
     *
     * @param chunkTag the tag the game writes, or null when it writes the chunk as empty
     * @return the tag to write: the given one (with the kept entities added) or, for an empty chunk
     *         that holds kept entities, a new tag with just them; null to write the chunk as empty
     */
    public static CompoundTag restore(String dimension, int chunkX, int chunkZ, CompoundTag chunkTag) {
        List<CompoundTag> kept = STASHES.get(key(dimension, chunkX, chunkZ));
        if (kept == null || kept.isEmpty()) {
            return chunkTag;
        }
        CompoundTag out = chunkTag;
        if (out == null) {
            out = NbtUtils.addCurrentDataVersion(new CompoundTag());
            out.put(ENTITIES, new ListTag());
            out.put("Position", new IntArrayTag(new int[]{chunkX, chunkZ}));
        }
        // Put back even when it was there: an empty list read with a type can be a detached copy.
        ListTag entities = Nbt.list(out, ENTITIES);
        for (CompoundTag entity : kept) {
            entities.add(entity.copy());
        }
        out.put(ENTITIES, entities);
        return out;
    }

    /** Entities per namespace in an entity chunk tag, riders included. */
    public static Map<String, Long> count(CompoundTag chunkTag) {
        Map<String, Long> counts = new HashMap<>();
        if (chunkTag != null) {
            countInto(Nbt.list(chunkTag, ENTITIES), counts);
        }
        return counts;
    }

    /** Per namespace, {entities, items they hold or are} in an entity chunk tag, as the count index takes them. */
    public static Map<String, long[]> countWithItems(CompoundTag chunkTag) {
        Map<String, long[]> counts = new HashMap<>();
        count(chunkTag).forEach((namespace, n) -> counts.computeIfAbsent(namespace, k -> new long[2])[0] += n);
        if (chunkTag != null) {
            MissingItems.count(Nbt.list(chunkTag, ENTITIES))
                    .forEach((namespace, n) -> counts.computeIfAbsent(namespace, k -> new long[2])[1] += n);
        }
        return counts;
    }

    private static void countInto(ListTag entities, Map<String, Long> counts) {
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = Nbt.compoundAt(entities, i);
            counts.merge(MissingBlocks.namespace(Nbt.stringOrEmpty(entity, "id")), 1L, Long::sum);
            countInto(Nbt.list(entity, PASSENGERS), counts);
        }
    }

    /** How many chunks have entities kept in memory. */
    static int stashCount() {
        return STASHES.size();
    }

    public static void clear() {
        STASHES.clear();
    }
}
