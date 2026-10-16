package io.github.kainrav.worldsure.mc.mixin;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.MissingBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The narrow hook into chunk reading and writing: after the game's data fixers ran and before the
 * block palette is decoded, missing blocks become placeholders; after a chunk is encoded for disk,
 * placeholders become the original blocks again and the chunk's modded objects are counted.
 */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {
    /** Snapshots made by copyOf, mapped to their dimension until write() encodes them on a worker thread. */
    @Unique
    private static final Map<SerializableChunkData, String> worldsure$dimensions = Collections.synchronizedMap(new IdentityHashMap<>());

    @Unique
    private static String worldsure$dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    @Inject(method = "parse", at = @At("HEAD"), cancellable = true)
    private static void worldsure$replaceMissing(LevelHeightAccessor levelHeight, PalettedContainerFactory containerFactory,
                                                CompoundTag chunkData, CallbackInfoReturnable<SerializableChunkData> cir) {
        if (!(levelHeight instanceof ServerLevel level)) {
            return;
        }
        CompoundTag cleaned;
        try {
            cleaned = MissingBlocks.replaceMissing(worldsure$dimension(level), chunkData);
        } catch (RuntimeException e) {
            // Never stand between the player and their chunk: fall back to the game's own handling.
            Worldsure.LOG.error("Could not keep missing blocks of a chunk; loading it as the game would", e);
            return;
        }
        if (cleaned != null) {
            // The cleaned copy holds no unknown ids, so this nested call passes straight through.
            cir.setReturnValue(SerializableChunkData.parse(levelHeight, containerFactory, cleaned));
        }
    }

    @Inject(method = "copyOf", at = @At("RETURN"))
    private static void worldsure$rememberDimension(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<SerializableChunkData> cir) {
        worldsure$dimensions.put(cir.getReturnValue(), worldsure$dimension(level));
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void worldsure$restoreAndCount(CallbackInfoReturnable<CompoundTag> cir) {
        SerializableChunkData self = (SerializableChunkData) (Object) this;
        String dimension = worldsure$dimensions.remove(self);
        if (dimension == null) {
            return;
        }
        CompoundTag tag = cir.getReturnValue();
        try {
            MissingBlocks.restore(dimension, tag);
        } catch (RuntimeException e) {
            // Failing this save is better than writing placeholders over the original blocks.
            Worldsure.LOG.error("Could not restore missing blocks of chunk {}; not saving it", self.chunkPos(), e);
            throw e;
        }
        try {
            Worldsure.counts().putBlocks(dimension, self.chunkPos().pack(), MissingBlocks.count(tag));
        } catch (RuntimeException e) {
            Worldsure.LOG.warn("Could not count objects in chunk {}", self.chunkPos(), e);
        }
    }
}
