package io.github.kainrav.worldsure.mc.mixin;
//#if >=1.21.5

import io.github.kainrav.worldsure.mc.MissingItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Since 1.21.5 saved data (chunks' block entities, entities, players) is read through TagValueInput.
 * Items of removed mods become placeholders before anything decodes them (see {@link MissingItems}).
 */
@Mixin(TagValueInput.class)
public abstract class TagValueInputMixin {
    @ModifyVariable(method = "create(Lnet/minecraft/util/ProblemReporter;Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/storage/ValueInput;",
            at = @At("HEAD"), argsOnly = true)
    private static CompoundTag worldsure$keepMissingItems(CompoundTag tag) {
        return MissingItems.wrap(tag);
    }

    @ModifyVariable(method = "create(Lnet/minecraft/util/ProblemReporter;Lnet/minecraft/core/HolderLookup$Provider;Ljava/util/List;)Lnet/minecraft/world/level/storage/ValueInput$ValueInputList;",
            at = @At("HEAD"), argsOnly = true)
    private static List<CompoundTag> worldsure$keepMissingItemsInList(List<CompoundTag> tags) {
        return tags.stream().map(MissingItems::wrap).toList();
    }
}
//#endif
