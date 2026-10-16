package io.github.kainrav.worldsure.mc.mixin;

import io.github.kainrav.worldsure.Worldsure;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/** Counts entities per mod whenever the game saves a chunk's entities. */
@Mixin(EntityStorage.class)
public abstract class EntityStorageMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "storeEntities", at = @At("HEAD"))
    private void worldsure$countEntities(ChunkEntities<Entity> chunk, CallbackInfo ci) {
        Map<String, Long> counts = new HashMap<>();
        chunk.getEntities().forEach(entity ->
                counts.merge(EntityType.getKey(entity.getType()).getNamespace(), 1L, Long::sum));
        Worldsure.counts().putEntities(level.dimension().identifier().toString(), chunk.getPos().pack(), counts);
    }
}
