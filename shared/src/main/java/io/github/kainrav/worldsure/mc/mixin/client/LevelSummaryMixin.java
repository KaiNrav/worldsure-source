package io.github.kainrav.worldsure.mc.mixin.client;

import io.github.kainrav.worldsure.mc.client.WorldsureClient;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks worlds whose mods changed since they were last played in the world list: their info line
 * (game mode, cheats, version) gets "⛨ Mods changed: N" added. The game builds that line once per summary.
 */
@Mixin(LevelSummary.class)
public abstract class LevelSummaryMixin {
    @Shadow
    public abstract String getLevelId();

    @Inject(method = "createInfo", at = @At("RETURN"), cancellable = true)
    private void worldsure$markChangedMods(CallbackInfoReturnable<Component> cir) {
        int changes = WorldsureClient.changesOf(getLevelId());
        if (changes > 0) {
            cir.setReturnValue(Component.empty().append(cir.getReturnValue()).append(" · ")
                    .append(Component.translatable("worldsure.world_list.changes", changes).withStyle(ChatFormatting.GOLD)));
        }
    }
}
