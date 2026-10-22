package io.github.kainrav.worldsure.mc;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
//#if >=1.20.5
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
//#else
//$ import net.minecraft.world.level.chunk.storage.IOWorker;
//#endif
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Game tests for {@link MissingEntities} and the entity storage hooks. */
public final class EntityGameTests {
    private static final String DIMENSION = "minecraft:overworld";
    private static final String MISSING_MOB = "fakemod:mob";

    private EntityGameTests() {
    }

    /**
     * Entities of a removed mod, standing alone or riding, leave an entity chunk on the way in and come
     * back with it on the way out; known entities stay as they were.
     */
    public static void missingEntitiesComeBack(GameTestHelper helper) {
        CompoundTag chicken = entity("minecraft:chicken");
        chicken.put("Passengers", list(entity("fakemod:tiny")));
        CompoundTag pig = entity("minecraft:pig");
        pig.put("Passengers", list(entity("fakemod:rider"), chicken));
        CompoundTag mob = entity(MISSING_MOB);
        mob.putInt("Energy", 1234);
        mob.put("Passengers", list(entity("minecraft:cow")));
        CompoundTag chunk = entityChunk(30_001, -30_001, pig, mob);

        CompoundTag loaded = MissingEntities.keepMissing(DIMENSION, chunk);
        helper.assertTrue(loaded != chunk, "the missing entities were not noticed");
        helper.assertTrue(ids(loaded).equals(List.of("minecraft:pig", "minecraft:chicken")), "loaded entities are " + ids(loaded));
        helper.assertTrue(ids(chunk).size() == 6, "the tag read from disk was changed: " + ids(chunk));
        CompoundTag same = entityChunk(30_002, -30_002, entity("minecraft:pig"));
        helper.assertTrue(MissingEntities.keepMissing(DIMENSION, same) == same, "a chunk without missing entities was copied");

        CompoundTag saved = MissingEntities.restore(DIMENSION, 30_001, -30_001, loaded.copy());
        List<String> savedIds = ids(saved);
        for (String id : List.of(MISSING_MOB, "minecraft:cow", "fakemod:rider", "fakemod:tiny", "minecraft:pig", "minecraft:chicken")) {
            helper.assertTrue(savedIds.contains(id), "the saved chunk lacks " + id + ": " + savedIds);
        }
        helper.assertTrue(savedIds.size() == 6, "the saved chunk holds " + savedIds);
        CompoundTag savedMob = Nbt.compounds(Nbt.list(saved, "Entities"))
                .filter(tag -> Nbt.stringOrEmpty(tag, "id").equals(MISSING_MOB))
                .findFirst()
                .orElse(new CompoundTag());
        helper.assertTrue(Nbt.intOr(savedMob, "Energy", 0) == 1234, "the kept mob lost its data");
        Long counted = MissingEntities.count(saved).get("fakemod");
        helper.assertTrue(counted != null && counted == 3, "counted " + MissingEntities.count(saved));

        // A chunk the game writes as empty still holds the kept entities.
        CompoundTag empty = MissingEntities.restore(DIMENSION, 30_001, -30_001, null);
        helper.assertTrue(empty != null && ids(empty).size() == 4, "an emptied chunk is written as " + (empty == null ? "nothing" : ids(empty)));
        helper.assertTrue(MissingEntities.restore(DIMENSION, 30_003, -30_003, null) == null, "a chunk without kept entities got some");
        helper.succeed();
    }

    /**
     * The level's real entity storage: a chunk file holding a mob of a removed mod loads without it,
     * and writing that chunk, now empty, keeps the mob in the file.
     */
    public static void entityStorageKeepsMissingEntities(GameTestHelper helper) {
        EntityStorage storage;
        Object raw;
        try {
            Object manager = field(helper.getLevel(), PersistentEntitySectionManager.class);
            storage = (EntityStorage) field(manager, EntityPersistentStorage.class);
            //#if >=1.20.5
            raw = field(storage, SimpleRegionStorage.class);
            //#else
            //$ raw = field(storage, IOWorker.class);
            //#endif
        } catch (ReflectiveOperationException | ClassCastException e) {
            helper.fail("could not reach the level's entity storage: " + e);
            return;
        }
        ChunkPos pos = new ChunkPos(30_010, -30_010);
        CompoundTag mob = entity(MISSING_MOB);
        mob.putInt("Energy", 4321);
        CompoundTag file = NbtUtils.addCurrentDataVersion(entityChunk(30_010, -30_010, mob, entity("minecraft:marker")));
        CompletableFuture<?> written = write(raw, pos, file);
        List<CompletableFuture<ChunkEntities<Entity>>> loading = new ArrayList<>();
        List<CompletableFuture<Optional<CompoundTag>>> reading = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(helper, written, "writing the chunk file"))
                .thenExecute(() -> loading.add(storage.loadEntities(pos)))
                .thenWaitUntil(() -> waitFor(helper, loading.get(0), "loading the chunk's entities"))
                .thenExecute(() -> {
                    List<String> types = loading.get(0).join().getEntities().map(e -> e.getType().toString()).toList();
                    helper.assertTrue(types.size() == 1, "loaded " + types + " instead of just the marker");
                    storage.storeEntities(new ChunkEntities<>(pos, List.of()));
                    reading.add(read(raw, pos));
                })
                .thenWaitUntil(() -> waitFor(helper, reading.get(0), "reading the chunk file back"))
                .thenExecute(() -> {
                    CompoundTag back = reading.get(0).join().orElse(null);
                    helper.assertTrue(back != null, "the chunk file is gone");
                    List<String> ids = ids(back);
                    helper.assertTrue(ids.equals(List.of(MISSING_MOB)), "the chunk file holds " + ids);
                    helper.assertTrue(Nbt.intOr(Nbt.compoundAt(Nbt.list(back, "Entities"), 0), "Energy", 0) == 4321,
                            "the kept mob lost its data");
                })
                .thenSucceed();
    }

    /**
     * Fails this tick unless the file work is done. A test server ticks without pause, so each wait also
     * gives the disk 10 ms: the test's tick budget then lasts long enough for a slow disk.
     */
    static void waitFor(GameTestHelper helper, CompletableFuture<?> work, String what) {
        if (!work.isDone()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        helper.assertTrue(work.isDone(), what);
    }

    private static CompletableFuture<?> write(Object raw, ChunkPos pos, CompoundTag tag) {
        //#if >=1.20.5
        return ((SimpleRegionStorage) raw).write(pos, tag);
        //#else
        //$ return ((IOWorker) raw).store(pos, tag);
        //#endif
    }

    private static CompletableFuture<Optional<CompoundTag>> read(Object raw, ChunkPos pos) {
        //#if >=1.20.5
        return ((SimpleRegionStorage) raw).read(pos);
        //#else
        //$ return ((IOWorker) raw).loadAsync(pos);
        //#endif
    }

    /** The value of the first instance field of a type, found by type so it works under any names. */
    static Object field(Object owner, Class<?> type) throws ReflectiveOperationException {
        for (Class<?> c = owner.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f.get(owner);
                }
            }
        }
        throw new NoSuchFieldException(type.getSimpleName() + " in " + owner.getClass().getName());
    }

    static CompoundTag entity(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        ListTag pos = new ListTag();
        for (double d : new double[]{480_160.5, 64, -480_160.5}) {
            pos.add(DoubleTag.valueOf(d));
        }
        tag.put("Pos", pos);
        return tag;
    }

    static ListTag list(CompoundTag... tags) {
        ListTag list = new ListTag();
        for (CompoundTag tag : tags) {
            list.add(tag);
        }
        return list;
    }

    private static CompoundTag entityChunk(int x, int z, CompoundTag... entities) {
        CompoundTag chunk = new CompoundTag();
        chunk.put("Position", new IntArrayTag(new int[]{x, z}));
        chunk.put("Entities", list(entities));
        return chunk;
    }

    /** Ids of the entities in an entity chunk, each followed by its riders. */
    static List<String> ids(CompoundTag chunk) {
        List<String> ids = new ArrayList<>();
        collectIds(Nbt.list(chunk, "Entities"), ids);
        return ids;
    }

    private static void collectIds(ListTag entities, List<String> ids) {
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = Nbt.compoundAt(entities, i);
            ids.add(Nbt.stringOrEmpty(entity, "id"));
            collectIds(Nbt.list(entity, "Passengers"), ids);
        }
    }
}
