package io.github.kainrav.worldsure.mc;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
//#if >=1.20.5
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//#endif
//#if >=1.21.5
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;
//#else
//$ import java.util.List;
//#endif
//#if <1.20.5
//$ import net.minecraft.world.level.Level;
//#endif

/** The placeholder for an item of a removed mod (see {@link MissingItems}); its tooltip names what it carries. */
public final class MissingItem extends Item {
    MissingItem(Properties properties) {
        super(properties);
    }

    //#if >=1.21.5
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        describe(stack, tooltip);
    }
    //#elif >=1.20.5
    //$ @Override
    //$ public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
    //$     describe(stack, tooltip::add);
    //$ }
    //#else
    //$ @Override
    //$ public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
    //$     describe(stack, tooltip::add);
    //$ }
    //#endif

    private static void describe(ItemStack stack, java.util.function.Consumer<Component> tooltip) {
        CompoundTag original = originalOf(stack);
        if (original == null) {
            tooltip.accept(Component.translatable("item.worldsure.missing_item.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        String id = Nbt.stringOrEmpty(original, "id");
        tooltip.accept(Component.literal(id + " ×" + MissingItems.stackSize(original)).withStyle(ChatFormatting.YELLOW));
        tooltip.accept(Component.translatable("item.worldsure.missing_item.hint").withStyle(ChatFormatting.GRAY));
    }

    /** The stack a placeholder carries, read from its own data; null if it carries none. */
    static CompoundTag originalOf(ItemStack stack) {
        //#if >=1.20.5
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        //#else
        //$ CompoundTag tag = stack.getTag();
        //$ if (tag == null) {
        //$     return null;
        //$ }
        //#endif
        return Nbt.compound(tag, MissingItems.ORIGINAL).orElse(null);
    }
}
