package io.github.kainrav.worldsure.mc.mixin;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.MissingEntities;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
//#if >=1.20.5
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#else
//$ import org.spongepowered.asm.mixin.injection.Inject;
//$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//#endif
//#if >=1.21.11
import java.util.function.Supplier;
//#endif

/**
 * The hook into entity chunk reading and writing: entities of removed mods are taken out of a chunk
 * before the game loads it (see {@link MissingEntities}) and written back with the chunk. Every write
 * also counts the chunk's entities per mod. Since 1.20.5 the read goes through the level's
 * SimpleRegionStorage (see SimpleRegionStorageMixin); before that through upgradeChunkTag here.
 */
@Mixin(EntityStorage.class)
public abstract class EntityStorageMixin {
    @Shadow
    @Final
    private ServerLevel level;

    //#if >=1.20.5
    @Shadow
    @Final
    private SimpleRegionStorage simpleRegionStorage;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void worldsure$track(CallbackInfo ci) {
        MissingEntities.track(simpleRegionStorage, WorldsureMc.dimension(level));
    }

    @ModifyArg(method = "storeEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"),
            index = 1)
    private CompoundTag worldsure$restore(ChunkPos pos, CompoundTag tag) {
        return worldsure$restoreAndCount(pos, tag);
    }
    //#else
    //$ @Inject(method = "upgradeChunkTag", at = @At("RETURN"), cancellable = true)
    //$ private void worldsure$keepMissing(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
    //$     cir.setReturnValue(WorldsureMc.keepMissingEntities(WorldsureMc.dimension(level), cir.getReturnValue()));
    //$ }
    //$
    //$ @ModifyArg(method = "storeEntities", at = @At(value = "INVOKE",
    //$         target = "Lnet/minecraft/world/level/chunk/storage/IOWorker;store(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/concurrent/CompletableFuture;"),
    //$         index = 1)
    //$ private CompoundTag worldsure$restore(ChunkPos pos, CompoundTag tag) {
    //$     return worldsure$restoreAndCount(pos, tag);
    //$ }
    //#endif

    //#if >=1.21.11
    /** An emptied chunk is written through a supplier of nothing; kept entities replace it. */
    @ModifyArg(method = "storeEntities", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/storage/SimpleRegionStorage;write(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
            index = 1)
    private Supplier<CompoundTag> worldsure$restoreEmpty(ChunkPos pos, Supplier<CompoundTag> empty) {
        CompoundTag kept = worldsure$restoreAndCount(pos, null);
        return kept == null ? empty : () -> kept;
    }
    //#endif

    @Unique
    private CompoundTag worldsure$restoreAndCount(ChunkPos pos, CompoundTag tag) {
        String dimension = WorldsureMc.dimension(level);
        CompoundTag out;
        try {
            out = MissingEntities.restore(dimension, WorldsureMc.chunkX(pos), WorldsureMc.chunkZ(pos), tag);
        } catch (RuntimeException e) {
            // Failing this save is better than writing the chunk without the kept entities.
            Worldsure.LOG.error("Could not restore entities of removed mods in chunk {}; not saving its entities", pos, e);
            throw e;
        }
        try {
            Worldsure.counts().putEntities(dimension, WorldsureMc.chunkKey(pos), MissingEntities.count(out));
        } catch (RuntimeException e) {
            Worldsure.LOG.warn("Could not count entities in chunk {}", pos, e);
        }
        return out;
    }
}
