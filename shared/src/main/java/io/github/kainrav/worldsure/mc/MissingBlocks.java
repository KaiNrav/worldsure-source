package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Keeps blocks of removed mods. When a chunk is read, every block whose id the game no longer
 * knows becomes a {@code worldsure:missing_block} placeholder, and the section as it was on disk
 * is kept in memory along with the block entities (chests, machines) of unknown types. When the
 * chunk is written, every placeholder still in place is turned back into the original block and
 * its block entity, so the file on disk never contains a placeholder.
 */
public final class MissingBlocks {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Worldsure.MOD_ID, "missing_block");
    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ID);
    private static final String PLACEHOLDER_NAME = ID.toString();

    /** dimension + chunk -> what the chunk held before placeholders went in */
    private static final Map<String, Stash> STASHES = new ConcurrentHashMap<>();

    private record Stash(Map<Integer, PackedSection<CompoundTag>> sections, List<CompoundTag> blockEntities) {
    }

    private MissingBlocks() {
    }

    public static Block create() {
        return new Block(BlockBehaviour.Properties.of()
                .setId(KEY)
                // Unbreakable and blast-proof, so the stored block is not lost by accident.
                .strength(-1.0F, 3_600_000.0F)
                .noLootTable());
    }

    private static String key(String dimension, long chunk) {
        return dimension + "@" + chunk;
    }

    static int globalBits() {
        return 32 - Integer.numberOfLeadingZeros(Block.BLOCK_STATE_REGISTRY.size() - 1);
    }

    private static boolean isMissing(CompoundTag entry) {
        Identifier id = Identifier.tryParse(entry.getStringOr("Name", ""));
        return id != null && !BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static boolean isPlaceholder(CompoundTag entry) {
        return PLACEHOLDER_NAME.equals(entry.getStringOr("Name", ""));
    }

    private static boolean isMissingBlockEntity(CompoundTag tag) {
        Identifier id = Identifier.tryParse(tag.getStringOr("id", ""));
        return id != null && !BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(id);
    }

    private static List<CompoundTag> palette(CompoundTag blockStates) {
        List<CompoundTag> palette = new ArrayList<>();
        blockStates.getListOrEmpty("palette").compoundStream().forEach(palette::add);
        return palette;
    }

    private static PackedSection<CompoundTag> read(CompoundTag blockStates) {
        return PackedSection.unpack(palette(blockStates), blockStates.getLongArray("data").orElse(null), globalBits());
    }

    private static void store(CompoundTag blockStates, PackedSection<CompoundTag> section) {
        ListTag palette = new ListTag();
        palette.addAll(section.palette());
        blockStates.put("palette", palette);
        long[] data = section.pack(globalBits());
        if (data == null) {
            blockStates.remove("data");
        } else {
            blockStates.put("data", new LongArrayTag(data));
        }
    }

    /**
     * Swaps blocks and block entities of removed mods for placeholders.
     *
     * @return a cleaned copy of the chunk tag, or {@code null} if it holds nothing missing
     */
    public static CompoundTag replaceMissing(String dimension, CompoundTag chunkTag) {
        ListTag sections = chunkTag.getListOrEmpty("sections");
        Map<Integer, PackedSection<CompoundTag>> originals = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompoundOrEmpty(i);
            CompoundTag blockStates = section.getCompound("block_states").orElse(null);
            if (blockStates != null && palette(blockStates).stream().anyMatch(MissingBlocks::isMissing)) {
                originals.put((int) section.getByteOr("Y", (byte) 0), read(blockStates));
            }
        }
        List<CompoundTag> missingEntities = chunkTag.getListOrEmpty("block_entities").compoundStream()
                .filter(MissingBlocks::isMissingBlockEntity)
                .toList();
        if (originals.isEmpty() && missingEntities.isEmpty()) {
            return null;
        }

        CompoundTag copy = chunkTag.copy();
        CompoundTag placeholder = new CompoundTag();
        placeholder.putString("Name", PLACEHOLDER_NAME);
        ListTag copiedSections = copy.getListOrEmpty("sections");
        for (int i = 0; i < copiedSections.size(); i++) {
            CompoundTag section = copiedSections.getCompoundOrEmpty(i);
            PackedSection<CompoundTag> original = originals.get((int) section.getByteOr("Y", (byte) 0));
            if (original != null) {
                store(section.getCompoundOrEmpty("block_states"), original.replace(MissingBlocks::isMissing, placeholder));
            }
        }
        if (!missingEntities.isEmpty()) {
            ListTag kept = new ListTag();
            copy.getListOrEmpty("block_entities").compoundStream()
                    .filter(Predicate.not(MissingBlocks::isMissingBlockEntity))
                    .forEach(kept::add);
            copy.put("block_entities", kept);
        }

        long chunk = chunkKey(chunkTag);
        STASHES.put(key(dimension, chunk), new Stash(originals, missingEntities));
        Worldsure.LOG.info("Kept {} section(s) and {} block entities of removed mods in chunk {} of {}",
                originals.size(), missingEntities.size(), chunk, dimension);
        return copy;
    }

    private static long chunkKey(CompoundTag chunkTag) {
        return ChunkPos.pack(chunkTag.getIntOr("xPos", 0), chunkTag.getIntOr("zPos", 0));
    }

    /** Puts the original blocks and block entities back into a chunk tag about to be written. */
    public static void restore(String dimension, CompoundTag chunkTag) {
        String key = key(dimension, chunkKey(chunkTag));
        Stash stash = STASHES.get(key);
        if (stash == null) {
            return;
        }
        Map<Integer, boolean[]> placeholderPositions = new HashMap<>();
        Map<Integer, PackedSection<CompoundTag>> stillNeeded = new HashMap<>();
        ListTag sections = chunkTag.getListOrEmpty("sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = sections.getCompoundOrEmpty(i);
            int y = section.getByteOr("Y", (byte) 0);
            PackedSection<CompoundTag> original = stash.sections().get(y);
            CompoundTag blockStates = section.getCompound("block_states").orElse(null);
            if (original == null || blockStates == null) {
                continue;
            }
            PackedSection<CompoundTag> current = read(blockStates);
            if (!current.contains(MissingBlocks::isPlaceholder)) {
                continue;
            }
            boolean[] positions = new boolean[PackedSection.SIZE];
            for (int p = 0; p < PackedSection.SIZE; p++) {
                positions[p] = isPlaceholder(current.get(p));
            }
            placeholderPositions.put(y, positions);
            stillNeeded.put(y, original);
            store(blockStates, current.restore(MissingBlocks::isPlaceholder, original));
        }

        List<CompoundTag> entitiesStillNeeded = new ArrayList<>();
        ListTag blockEntities = chunkTag.getListOrEmpty("block_entities");
        for (CompoundTag entity : stash.blockEntities()) {
            int x = entity.getIntOr("x", 0);
            int y = entity.getIntOr("y", 0);
            int z = entity.getIntOr("z", 0);
            boolean[] positions = placeholderPositions.get(y >> 4);
            if (positions != null && positions[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)]) {
                blockEntities.add(entity.copy());
                entitiesStillNeeded.add(entity);
            }
        }
        if (!chunkTag.contains("block_entities") && !blockEntities.isEmpty()) {
            chunkTag.put("block_entities", blockEntities);
        }

        // Placeholders broken on purpose (in creative) no longer need their originals.
        if (stillNeeded.isEmpty()) {
            STASHES.remove(key);
        } else if (stillNeeded.size() != stash.sections().size() || entitiesStillNeeded.size() != stash.blockEntities().size()) {
            STASHES.put(key, new Stash(stillNeeded, entitiesStillNeeded));
        }
    }

    /** Counts blocks and block entities per namespace in a chunk tag as it goes to disk. */
    public static Map<String, long[]> count(CompoundTag chunkTag) {
        Map<String, long[]> counts = new HashMap<>();
        ListTag sections = chunkTag.getListOrEmpty("sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag blockStates = sections.getCompoundOrEmpty(i).getCompound("block_states").orElse(null);
            if (blockStates == null) {
                continue;
            }
            read(blockStates).countBy(entry -> namespace(entry.getStringOr("Name", "")))
                    .forEach((namespace, n) -> counts.computeIfAbsent(namespace, k -> new long[2])[0] += n);
        }
        chunkTag.getListOrEmpty("block_entities").compoundStream()
                .forEach(tag -> counts.computeIfAbsent(namespace(tag.getStringOr("id", "")), k -> new long[2])[1]++);
        return counts;
    }

    static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    public static void clear() {
        STASHES.clear();
    }
}
