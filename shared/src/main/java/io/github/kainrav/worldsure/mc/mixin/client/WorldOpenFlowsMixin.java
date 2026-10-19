package io.github.kainrav.worldsure.mc.mixin.client;

import io.github.kainrav.worldsure.mc.client.WorldsureClient;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows the Worldsure screen before a single player world whose mods changed starts loading.
 * Every way of opening an existing world (world list, quick play) goes through openWorld, called
 * loadLevel before 1.20.3.
 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Shadow
    @Final
    private LevelStorageSource levelSource;

    //#if >=1.20.3
    @Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
    private void worldsure$confirmChangedMods(String levelId, Runnable onCancel, CallbackInfo ci) {
        WorldOpenFlows self = (WorldOpenFlows) (Object) this;
        if (WorldsureClient.interceptOpen(levelSource.getLevelPath(levelId),
                () -> self.openWorld(levelId, onCancel), onCancel)) {
            ci.cancel();
        }
    }
    //#else
    //$ @Inject(method = "loadLevel", at = @At("HEAD"), cancellable = true)
    //$ private void worldsure$confirmChangedMods(net.minecraft.client.gui.screens.Screen lastScreen, String levelId, CallbackInfo ci) {
    //$     WorldOpenFlows self = (WorldOpenFlows) (Object) this;
    //$     if (WorldsureClient.interceptOpen(levelSource.getBaseDir().resolve(levelId),
    //$             () -> self.loadLevel(lastScreen, levelId), () -> WorldsureClient.setScreen(lastScreen))) {
    //$         ci.cancel();
    //$     }
    //$ }
    //#endif
}
