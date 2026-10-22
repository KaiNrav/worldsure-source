package io.github.kainrav.worldsure.mc.mixin;
//#if >=1.21.5

import io.github.kainrav.worldsure.mc.MissingItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Since 1.21.5 saved data is written through TagValueOutput; placeholders turn back into their items here. */
@Mixin(TagValueOutput.class)
public abstract class TagValueOutputMixin {
    @Inject(method = "buildResult", at = @At("RETURN"))
    private void worldsure$restoreMissingItems(CallbackInfoReturnable<CompoundTag> cir) {
        MissingItems.unwrap(cir.getReturnValue());
    }
}
//#endif
