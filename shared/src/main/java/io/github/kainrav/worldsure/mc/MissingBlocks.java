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
 *
 * <p>Biomes of removed mods are kept the same way: they load as plains, and every spot whose biome
 * is still the stand-in when the chunk is written gets its original biome back; a spot changed in
 * the meantime (with /fillbiome, say) keeps the new biome. Once no stand-in is left, the originals
 * are let go.
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

    /** dimension + chunk -> what the chunk held before placeholders went in */
    private static final Map<String, Stash> STASHES = new ConcurrentHashMap<>();

    private static final StringTag BIOME_STAND_IN = StringTag.valueOf("minecraft:plains");

    /**
     * @param sections      section Y -> block states as on disk, for sections holding placeholders
     * @param blockEntities block entities of unknown types
     * @param biomes        section Y -> biomes {as on disk, as loaded}, for sections with unknown biomes
     */
    private record Stash(Map<Integer, PackedSection<Tag>> sections, List<CompoundTag> blockEntities,
                         Map<Integer, List<PackedSection<Tag>>> biomes) {
        boolean isEmpty() {
            return sections.isEmpty() && blockEntities.isEmpty() && biomes.isEmpty();
        }
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

    /** The block missing blocks turn into: Worldsure's own, or bedrock in server-only mode. */
    private static String placeholderName() {
        return Placeholders.serverOnly() ? Placeholders.VANILLA_BLOCK : PLACEHOLDER_NAME;
    }

    /** The palette entry of the placeholder block; both kinds have a single state. */
    private static Tag placeholder() {
        //#if >=26.3
        // Since 26.3 a block with a single state is stored as its bare id; others as {id, properties}.
        return StringTag.valueOf(placeholderName());
        //#else
        //$ CompoundTag entry = new CompoundTag();
        //$ entry.putString("Name", placeholderName());
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

    /** Either kind of placeholder; bedrock only counts where the section held a missing block (see restore). */
    private static boolean isPlaceholder(Tag entry) {
        String id = blockId(entry);
        return PLACEHOLDER_NAME.equals(id) || Placeholders.VANILLA_BLOCK.equals(id);
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

    private static List<Tag> biomePalette(CompoundTag biomes) {
        return new ArrayList<>(Nbt.stringList(biomes, "palette"));
    }

    private static PackedSection<Tag> readBiomes(CompoundTag biomes) {
        return PackedSection.unpackBiomes(biomePalette(biomes), Nbt.longArray(biomes, "data").orElse(null));
    }

    private static void storeBiomes(CompoundTag biomes, PackedSection<Tag> section) {
        ListTag palette = new ListTag();
        palette.addAll(section.palette());
        biomes.put("palette", palette);
        long[] data = section.packBiomes();
        if (data == null) {
            biomes.remove("data");
        } else {
            biomes.put("data", new LongArrayTag(data));
        }
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
        return replaceMissing(dimension, chunkTag, biome -> false);
    }

    /**
     * Swaps blocks, block entities and biomes of removed mods for placeholders.
     *
     * @param unknownBiome whether the level knows no biome of this id
     * @return a cleaned copy of the chunk tag, or {@code null} if it holds nothing missing
     */
    public static CompoundTag replaceMissing(String dimension, CompoundTag chunkTag, Predicate<String> unknownBiome) {
        ListTag sections = Nbt.list(chunkTag, "sections");
        Map<Integer, PackedSection<Tag>> originals = new HashMap<>();
        Map<Integer, List<PackedSection<Tag>>> biomes = new HashMap<>();
        Predicate<Tag> isMissingBiome = entry -> entry instanceof StringTag id && unknownBiome.test(Nbt.value(id));
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(sections, i);
            int y = Nbt.byteOr(section, "Y", (byte) 0);
            CompoundTag blockStates = Nbt.compound(section, "block_states").orElse(null);
            if (blockStates != null && palette(blockStates).stream().anyMatch(MissingBlocks::isMissing)) {
                originals.put(y, read(blockStates));
            }
            CompoundTag biomeTag = Nbt.compound(section, "biomes").orElse(null);
            if (biomeTag != null && biomePalette(biomeTag).stream().anyMatch(isMissingBiome)) {
                PackedSection<Tag> original = readBiomes(biomeTag);
                biomes.put(y, List.of(original, original.replace(isMissingBiome, BIOME_STAND_IN)));
            }
        }
        List<CompoundTag> missingEntities = Nbt.compounds(Nbt.list(chunkTag, "block_entities"))
                .filter(MissingBlocks::isMissingBlockEntity)
                .toList();
        if (originals.isEmpty() && missingEntities.isEmpty() && biomes.isEmpty()) {
            return null;
        }

        CompoundTag copy = chunkTag.copy();
        ListTag copiedSections = Nbt.list(copy, "sections");
        for (int i = 0; i < copiedSections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(copiedSections, i);
            int y = Nbt.byteOr(section, "Y", (byte) 0);
            PackedSection<Tag> original = originals.get(y);
            if (original != null) {
                store(Nbt.compound(section, "block_states").orElseThrow(), original.replace(MissingBlocks::isMissing, placeholder()));
            }
            List<PackedSection<Tag>> biome = biomes.get(y);
            if (biome != null) {
                storeBiomes(Nbt.compound(section, "biomes").orElseThrow(), biome.get(1));
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
        STASHES.put(key(dimension, chunk), new Stash(originals, missingEntities, biomes));
        if (!originals.isEmpty() || !missingEntities.isEmpty()) {
            Worldsure.LOG.info("Kept {} section(s) and {} block entities of removed mods in chunk {} of {}",
                    originals.size(), missingEntities.size(), chunk, dimension);
        }
        if (!biomes.isEmpty()) {
            Worldsure.LOG.info("Kept the biomes of removed mods in {} section(s) of chunk {} of {}", biomes.size(), chunk, dimension);
        }
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
        Map<Integer, List<PackedSection<Tag>>> biomesStillNeeded = new HashMap<>();
        ListTag sections = Nbt.list(chunkTag, "sections");
        for (int i = 0; i < sections.size(); i++) {
            CompoundTag section = Nbt.compoundAt(sections, i);
            int y = Nbt.byteOr(section, "Y", (byte) 0);
            List<PackedSection<Tag>> biome = stash.biomes().get(y);
            CompoundTag biomeTag = Nbt.compound(section, "biomes").orElse(null);
            if (biome != null && biomeTag != null) {
                PackedSection<Tag> current = readBiomes(biomeTag);
                storeBiomes(biomeTag, current.restoreUnchanged(biome.get(1), biome.get(0)));
                // Kept as long as some spot still holds the stand-in for a biome of a removed mod.
                for (int p = 0; p < current.size(); p++) {
                    if (!biome.get(0).get(p).equals(biome.get(1).get(p)) && current.get(p).equals(biome.get(1).get(p))) {
                        biomesStillNeeded.put(y, biome);
                        break;
                    }
                }
            }
            PackedSection<Tag> original = stash.sections().get(y);
            CompoundTag blockStates = Nbt.compound(section, "block_states").orElse(null);
            if (original == null || blockStates == null) {
                continue;
            }
            PackedSection<Tag> current = read(blockStates);
            // A spot comes back only where the disk held a missing block and a placeholder still stands, so
            // real bedrock (and bedrock a player placed) stays bedrock in server-only mode.
            boolean[] missing = new boolean[original.palette().size()];
            for (int e = 0; e < missing.length; e++) {
                missing[e] = isMissing(original.palette().get(e));
            }
            boolean[] positions = new boolean[PackedSection.SIZE];
            boolean any = false;
            for (int p = 0; p < PackedSection.SIZE; p++) {
                positions[p] = missing[original.indices()[p]] && isPlaceholder(current.get(p));
                any |= positions[p];
            }
            if (!any) {
                continue;
            }
            placeholderPositions.put(y, positions);
            stillNeeded.put(y, original);
            store(blockStates, current.restoreAt(positions, original));
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

        // Placeholders broken on purpose (in creative) and biomes changed everywhere no longer need their originals.
        Stash left = new Stash(stillNeeded, entitiesStillNeeded, biomesStillNeeded);
        if (left.isEmpty()) {
            STASHES.remove(key);
        } else if (stillNeeded.size() != stash.sections().size() || entitiesStillNeeded.size() != stash.blockEntities().size()
                || biomesStillNeeded.size() != stash.biomes().size()) {
            STASHES.put(key, left);
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

    /** How many chunks have something kept in memory. */
    static int stashCount() {
        return STASHES.size();
    }

    public static void clear() {
        STASHES.clear();
    }
}
