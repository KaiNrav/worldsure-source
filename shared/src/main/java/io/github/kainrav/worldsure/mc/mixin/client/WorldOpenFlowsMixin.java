package io.github.kainrav.worldsure.mc.mixin.client;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.client.ConfirmModChangesScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Optional;

/** Shows the confirmation screen before a single player world whose mods changed starts loading. */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private LevelStorageSource levelSource;

    @Inject(method = "openWorld", at = @At("HEAD"), cancellable = true)
    private void worldsure$confirmChangedMods(String levelId, Runnable onCancel, CallbackInfo ci) {
        Path worldDir = levelSource.getLevelPath(levelId).toAbsolutePath().normalize();
        if (ConfirmModChangesScreen.consumeConfirmed(worldDir)) {
            return;
        }
        Optional<Worldsure.Check> check = Worldsure.check(worldDir, Platform.get().installedMods());
        if (check.isEmpty()) {
            return;
        }
        ci.cancel();
        WorldOpenFlows self = (WorldOpenFlows) (Object) this;
        minecraft.gui.setScreen(new ConfirmModChangesScreen(check.get(), worldDir,
                () -> self.openWorld(levelId, onCancel), onCancel));
    }
}
