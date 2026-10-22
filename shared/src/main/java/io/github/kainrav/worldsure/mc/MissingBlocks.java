package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.PackedSection;
import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
//#if >=1.21.11
import net.minecraft.resources.Identifier;
//#else
//$ import net.minecraft.resources.ResourceLocation;
//#endif
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
    //#if >=1.21.11
    public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Worldsure.MOD_ID, "missing_block"));
    //#elif >=1.21
    //$ public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Worldsure.MOD_ID, "missing_block"));
    //#else
    //$ public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, new ResourceLocation(Worldsure.MOD_ID, "missing_block"));
    //#endif
    private static final String PLACEHOLDER_NAME = Worldsure.MOD_ID + ":missing_block";
    private static final Tag PLACEHOLDER = placeholder();

    /** dimension + chunk -> what the chunk held before placeholders went in */
    private static final Map<String, Stash> STASHES = new ConcurrentHashMap<>();

    private record Stash(Map<Integer, PackedSection<Tag>> sections, List<CompoundTag> blockEntities) {
    }

    private MissingBlocks() {
    }

    public static Block create() {
        return new Block(BlockBehaviour.Properties.of()
                //#if >=1.21.2
                .setId(KEY)
                //#endif
                // Unbreakable and blast-proof, so the stored block is not lost by accident.
                .strength(-1.0F, 3_600_000.0F)
                .noLootTable());
    }

    /** The palette entry of the placeholder block, which has a single state. */
    private static Tag placeholder() {
        //#if >=26.3
        // Since 26.3 a block with a single state is stored as its bare id; others as {id, properties}.
        return StringTag.valueOf(PLACEHOLDER_NAME);
        //#else
        //$ CompoundTag entry = new CompoundTag();
        //$ entry.putString("Name", PLACEHOLDER_NAME);
        //$ return entry;
        //#endif
    }

    private static String key(String dimension, long chunk) {
        return dimension + "@" + chunk;
    }

    static int globalBits() {
        return 32 - Integer.numberOfLeadingZeros(Block.BLOCK_STATE_REGISTRY.size() - 1);
    }

    /** The block id of a palette entry: a bare id string, or a compound with "id" (older worlds used "Name"). */
    static String blockId(Tag entry) {
        if (entry instanceof StringTag string) {
            return Nbt.value(string);
        }
        if (entry instanceof CompoundTag compound) {
            return Nbt.string(compound, "id").or(() -> Nbt.string(compound, "Name")).orElse("");
        }
        return "";
    }

    private static boolean isMissing(Tag entry) {
        return isUnknown(blockId(entry), true);
    }

    /** Whether an id parses but names no registered block (or block entity type). */
    private static boolean isUnknown(String text, boolean block) {
        return isUnknown(text, block ? BuiltInRegistries.BLOCK : BuiltInRegistries.BLOCK_ENTITY_TYPE);
    }

    /** Whether an id parses but names nothing in a registry, the way objects of a removed mod look. */
    static boolean isUnknown(String text, Registry<?> registry) {
        //#if >=1.21.11
        Identifier id = Identifier.tryParse(text);
        //#else
        //$ ResourceLocation id = ResourceLocation.tryParse(text);
        //#endif
        return id != null && !registry.containsKey(id);
    }

    private static boolean isPlaceholder(Tag entry) {
        return PLACEHOLDER_NAME.equals(blockId(entry));
    }

    private static boolean isMissingBlockEntity(CompoundTag tag) {
        return isUnknown(Nbt.stringOrEmpty(tag, "id"), false);
    }

    private static List<Tag> palette(CompoundTag blockStates) {
        return new ArrayList<>(Nbt.list(blockStates, "palette"));
    }

    private static PackedSection<Tag> read(CompoundTag blockStates) {
        return PackedSection.unpack(palette(blockStates), Nbt.longArray(blockStates, "data").orElse(null), globalBits());
    }

    private static void store(CompoundTag blockStates, PackedSection<Tag> section) {
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
        ListTag sections = Nbt.list(chunkTag, "sections");
        Map<Integer, PackedSection<Tag>> originals = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(sections, i);
            CompoundTag blockStates = Nbt.compound(section, "block_states").orElse(null);
            if (blockStates != null && palette(blockStates).stream().anyMatch(MissingBlocks::isMissing)) {
                originals.put((int) Nbt.byteOr(section, "Y", (byte) 0), read(blockStates));
            }
        }
        List<CompoundTag> missingEntities = Nbt.compounds(Nbt.list(chunkTag, "block_entities"))
                .filter(MissingBlocks::isMissingBlockEntity)
                .toList();
        if (originals.isEmpty() && missingEntities.isEmpty()) {
            return null;
        }

        CompoundTag copy = chunkTag.copy();
        ListTag copiedSections = Nbt.list(copy, "sections");
        for (int i = 0; i < copiedSections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(copiedSections, i);
            PackedSection<Tag> original = originals.get((int) Nbt.byteOr(section, "Y", (byte) 0));
            if (original != null) {
                store(Nbt.compound(section, "block_states").orElseThrow(), original.replace(MissingBlocks::isMissing, PLACEHOLDER));
            }
        }
        if (!missingEntities.isEmpty()) {
            ListTag kept = new ListTag();
            Nbt.compounds(Nbt.list(copy, "block_entities"))
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
        return chunkKey(Nbt.intOr(chunkTag, "xPos", 0), Nbt.intOr(chunkTag, "zPos", 0));
    }

    /** The long a chunk position packs into, as the game itself keys chunks. */
    public static long chunkKey(int x, int z) {
        //#if >=26.1
        return ChunkPos.pack(x, z);
        //#else
        //$ return ChunkPos.asLong(x, z);
        //#endif
    }

    /** Puts the original blocks and block entities back into a chunk tag about to be written. */
    public static void restore(String dimension, CompoundTag chunkTag) {
        String key = key(dimension, chunkKey(chunkTag));
        Stash stash = STASHES.get(key);
        if (stash == null) {
            return;
        }
        Map<Integer, boolean[]> placeholderPositions = new HashMap<>();
        Map<Integer, PackedSection<Tag>> stillNeeded = new HashMap<>();
        ListTag sections = Nbt.list(chunkTag, "sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(sections, i);
            int y = Nbt.byteOr(section, "Y", (byte) 0);
            PackedSection<Tag> original = stash.sections().get(y);
            CompoundTag blockStates = Nbt.compound(section, "block_states").orElse(null);
            if (original == null || blockStates == null) {
                continue;
            }
            PackedSection<Tag> current = read(blockStates);
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
        ListTag blockEntities = Nbt.list(chunkTag, "block_entities");
        for (CompoundTag entity : stash.blockEntities()) {
            int x = Nbt.intOr(entity, "x", 0);
            int y = Nbt.intOr(entity, "y", 0);
            int z = Nbt.intOr(entity, "z", 0);
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

    /** Counts blocks, block entities and the items they hold per namespace in a chunk tag as it goes to disk. */
    public static Map<String, long[]> count(CompoundTag chunkTag) {
        Map<String, long[]> counts = new HashMap<>();
        ListTag sections = Nbt.list(chunkTag, "sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag blockStates = Nbt.compound(Nbt.compoundAt(sections, i), "block_states").orElse(null);
            if (blockStates == null) {
                continue;
            }
            read(blockStates).countBy(entry -> namespace(blockId(entry)))
                    .forEach((namespace, n) -> counts.computeIfAbsent(namespace, k -> new long[3])[0] += n);
        }
        Nbt.compounds(Nbt.list(chunkTag, "block_entities"))
                .forEach(tag -> counts.computeIfAbsent(namespace(Nbt.stringOrEmpty(tag, "id")), k -> new long[3])[1]++);
        MissingItems.count(Nbt.list(chunkTag, "block_entities"))
                .forEach((namespace, n) -> counts.computeIfAbsent(namespace, k -> new long[3])[2] += n);
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
