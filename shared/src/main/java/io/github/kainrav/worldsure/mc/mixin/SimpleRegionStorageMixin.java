package io.github.kainrav.worldsure.mc.mixin;
//#if >=1.20.5

import io.github.kainrav.worldsure.mc.MissingEntities;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity chunks are upgraded here right before the game loads their entities. Only storages that
 * belong to a running level's entity storage are cleaned (see EntityStorageMixin), so world upgrades
 * and other readers of the same files see them untouched.
 */
@Mixin(SimpleRegionStorage.class)
public abstract class SimpleRegionStorageMixin {
    @Inject(method = "upgradeChunkTag(Lnet/minecraft/nbt/CompoundTag;I)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), cancellable = true)
    private void worldsure$keepMissingEntities(CompoundTag tag, int defaultVersion, CallbackInfoReturnable<CompoundTag> cir) {
        String dimension = MissingEntities.dimensionOf(this);
        if (dimension != null) {
            cir.setReturnValue(WorldsureMc.keepMissingEntities(dimension, cir.getReturnValue()));
        }
    }
}
//#endif
