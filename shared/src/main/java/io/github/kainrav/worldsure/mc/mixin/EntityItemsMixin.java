package io.github.kainrav.worldsure.mc.mixin;
//#if <1.21.5
//$
//$ import io.github.kainrav.worldsure.mc.MissingItems;
//$ import net.minecraft.nbt.CompoundTag;
//$ import net.minecraft.world.entity.Entity;
//$ import org.spongepowered.asm.mixin.Mixin;
//$ import org.spongepowered.asm.mixin.injection.At;
//$ import org.spongepowered.asm.mixin.injection.Inject;
//$ import org.spongepowered.asm.mixin.injection.ModifyVariable;
//$ import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//$
//$ /**
//$  * Before 1.21.5 every entity, players included, is read through Entity.load and written through
//$  * saveWithoutId: items of removed mods become placeholders on the way in and come back on the way out.
//$  */
//$ @Mixin(Entity.class)
//$ public abstract class EntityItemsMixin {
//$     @ModifyVariable(method = "load", at = @At("HEAD"), argsOnly = true)
//$     private CompoundTag worldsure$keepMissingItems(CompoundTag tag) {
//$         return MissingItems.wrap(tag);
//$     }
//$
//$     @Inject(method = "saveWithoutId", at = @At("RETURN"))
//$     private void worldsure$restoreMissingItems(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
//$         MissingItems.unwrap(cir.getReturnValue());
//$     }
//$ }
//#endif
