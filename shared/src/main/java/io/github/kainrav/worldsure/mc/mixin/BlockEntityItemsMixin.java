package io.github.kainrav.worldsure.mc.mixin;
//#if <1.21.5
//$
//$ import io.github.kainrav.worldsure.mc.MissingItems;
//$ import net.minecraft.nbt.CompoundTag;
//$ import net.minecraft.world.level.block.entity.BlockEntity;
//$ import org.spongepowered.asm.mixin.Mixin;
//$ import org.spongepowered.asm.mixin.injection.At;
//$ import org.spongepowered.asm.mixin.injection.Inject;
//$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$
//$ /**
//$  * Before 1.21.5 block entities are created from saved data in loadStatic and written through
//$  * saveWithoutMetadata: items of removed mods in chests and machines become placeholders on the way
//$  * in and come back on the way out.
//$  */
//$ @Mixin(BlockEntity.class)
//$ public abstract class BlockEntityItemsMixin {
//$     @ModifyVariable(method = "loadStatic", at = @At("HEAD"), argsOnly = true)
//$     private static CompoundTag worldsure$keepMissingItems(CompoundTag tag) {
//$         return MissingItems.wrap(tag);
//$     }
//$
//$     @Inject(method = "saveWithoutMetadata", at = @At("RETURN"))
//$     private void worldsure$restoreMissingItems(CallbackInfoReturnable<CompoundTag> cir) {
//$         MissingItems.unwrap(cir.getReturnValue());
//$     }
//$ }
//#endif
