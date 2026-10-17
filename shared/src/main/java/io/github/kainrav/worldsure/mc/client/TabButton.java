package io.github.kainrav.worldsure.mc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/** A pill-shaped tab; the selected one is tinted with the accent colour. */
final class TabButton extends AbstractButton {
    private final BooleanSupplier selected;
    private final Runnable onSelect;

    TabButton(int x, int y, Component message, BooleanSupplier selected, Runnable onSelect) {
        super(x, y, Minecraft.getInstance().font.width(message) + 14, 14, message);
        this.selected = selected;
        this.onSelect = onSelect;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onSelect.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean isSelected = selected.getAsBoolean();
        int textColor;
        if (isSelected) {
            Theme.rounded(graphics, getX(), getY(), getRight(), getBottom(), Theme.alpha(Theme.ACCENT, 0x38));
            Theme.roundedOutline(graphics, getX(), getY(), getRight(), getBottom(), Theme.alpha(Theme.ACCENT, 0xB0));
            textColor = Theme.ACCENT;
        } else if (isHoveredOrFocused()) {
            Theme.rounded(graphics, getX(), getY(), getRight(), getBottom(), 0x18FFFFFF);
            textColor = Theme.TEXT;
        } else {
            textColor = Theme.MUTED;
        }
        Font font = Minecraft.getInstance().font;
        graphics.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 3, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
