package io.github.kainrav.worldsure.mc.mixin;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.MissingBlocks;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#if >=1.21.2
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
//#if >=1.21.11
import net.minecraft.world.level.chunk.PalettedContainerFactory;
//#else
//$ import net.minecraft.core.RegistryAccess;
//#endif
//#else
//$ import net.minecraft.world.entity.ai.village.poi.PoiManager;
//$ import net.minecraft.world.level.chunk.ProtoChunk;
//$ import net.minecraft.world.level.chunk.storage.ChunkSerializer;
//#if >=1.21
//$ import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
//#endif
//#endif

/**
 * The narrow hook into chunk reading and writing: after the game's data fixers ran and before the
 * block palette is decoded, missing blocks become placeholders; after a chunk is encoded for disk,
 * placeholders become the original blocks again and the chunk's modded objects are counted.
 * Since 1.21.2 chunks go through SerializableChunkData; before that through ChunkSerializer.
 */
//#if >=1.21.2
@Mixin(SerializableChunkData.class)
//#else
//$ @Mixin(ChunkSerializer.class)
//#endif
public abstract class ChunkDataMixin {
    //#if >=1.21.2
    /** Snapshots made by copyOf, mapped to their dimension until write() encodes them on a worker thread. */
    @Unique
    private static final Map<SerializableChunkData, String> worldsure$dimensions = Collections.synchronizedMap(new IdentityHashMap<>());

    @Inject(method = "parse", at = @At("HEAD"), cancellable = true)
    //#if >=1.21.11
    private static void worldsure$replaceMissing(LevelHeightAccessor levelHeight, PalettedContainerFactory containerFactory,
                                                CompoundTag chunkData, CallbackInfoReturnable<SerializableChunkData> cir) {
    //#else
    //$ private static void worldsure$replaceMissing(LevelHeightAccessor levelHeight, RegistryAccess containerFactory,
    //$                                             CompoundTag chunkData, CallbackInfoReturnable<SerializableChunkData> cir) {
    //#endif
        if (!(levelHeight instanceof ServerLevel level)) {
            return;
        }
        CompoundTag cleaned = worldsure$clean(level, chunkData);
        if (cleaned != null) {
            // The cleaned copy holds no unknown ids, so this nested call passes straight through.
            cir.setReturnValue(SerializableChunkData.parse(levelHeight, containerFactory, cleaned));
        }
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void worldsure$rememberDimension(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<SerializableChunkData> cir) {
        worldsure$dimensions.put(cir.getReturnValue(), WorldsureMc.dimension(level));
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void worldsure$restoreAndCount(CallbackInfoReturnable<CompoundTag> cir) {
        SerializableChunkData self = (SerializableChunkData) (Object) this;
        String dimension = worldsure$dimensions.remove(self);
        if (dimension != null) {
            worldsure$restore(dimension, self.chunkPos(), cir.getReturnValue());
        }
    }
    //#else
    //$ @Inject(method = "read", at = @At("HEAD"), cancellable = true)
    //#if >=1.21
    //$ private static void worldsure$replaceMissing(ServerLevel level, PoiManager poiManager, RegionStorageInfo storageInfo, ChunkPos pos,
    //$                                             CompoundTag chunkData, CallbackInfoReturnable<ProtoChunk> cir) {
    //#else
    //$ private static void worldsure$replaceMissing(ServerLevel level, PoiManager poiManager, ChunkPos pos,
    //$                                             CompoundTag chunkData, CallbackInfoReturnable<ProtoChunk> cir) {
    //#endif
    //$     CompoundTag cleaned = worldsure$clean(level, chunkData);
    //$     if (cleaned != null) {
    //$         // The cleaned copy holds no unknown ids, so this nested call passes straight through.
    //#if >=1.21
    //$         cir.setReturnValue(ChunkSerializer.read(level, poiManager, storageInfo, pos, cleaned));
    //#else
    //$         cir.setReturnValue(ChunkSerializer.read(level, poiManager, pos, cleaned));
    //#endif
    //$     }
    //$ }
    //$
    //$ @Inject(method = "write", at = @At("RETURN"))
    //$ private static void worldsure$restoreAndCount(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<CompoundTag> cir) {
    //$     worldsure$restore(WorldsureMc.dimension(level), chunk.getPos(), cir.getReturnValue());
    //$ }
    //#endif

    /** The chunk tag with placeholders for missing blocks, or null to load it as the game would. */
    @Unique
    private static CompoundTag worldsure$clean(ServerLevel level, CompoundTag chunkData) {
        try {
            return MissingBlocks.replaceMissing(WorldsureMc.dimension(level), chunkData);
        } catch (RuntimeException e) {
            // Never stand between the player and their chunk: fall back to the game's own handling.
            Worldsure.LOG.error("Could not keep missing blocks of a chunk; loading it as the game would", e);
            return null;
        }
    }

    @Unique
    private static void worldsure$restore(String dimension, ChunkPos pos, CompoundTag tag) {
        try {
            MissingBlocks.restore(dimension, tag);
        } catch (RuntimeException e) {
            // Failing this save is better than writing placeholders over the original blocks.
            Worldsure.LOG.error("Could not restore missing blocks of chunk {}; not saving it", pos, e);
            throw e;
        }
        try {
            Worldsure.counts().putBlocks(dimension, WorldsureMc.chunkKey(pos), MissingBlocks.count(tag));
        } catch (RuntimeException e) {
            Worldsure.LOG.warn("Could not count objects in chunk {}", pos, e);
        }
    }
}
