package io.github.kainrav.worldsure.mc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
//#if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$ import net.minecraft.client.gui.GuiGraphics;
//#endif
//#if >=1.21.9
import net.minecraft.client.input.InputWithModifiers;
//#endif

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

    //#if >=1.21.9
    @Override
    public void onPress(InputWithModifiers input) {
        onSelect.run();
    }
    //#else
    //$ @Override
    //$ public void onPress() {
    //$     onSelect.run();
    //$ }
    //#endif

    //#if >=26.1
    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        draw(new Gfx(graphics));
    }
    //#elif >=1.21.11
    //$ @Override
    //$ protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float a) {
    //$     draw(new Gfx(graphics));
    //$ }
    //#else
    //$ @Override
    //$ protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
    //$     draw(new Gfx(graphics));
    //$ }
    //#endif

    private void draw(Gfx graphics) {
        boolean isSelected = selected.getAsBoolean();
        int textColor;
        if (isSelected) {
            Theme.rounded(graphics, getX(), getY(), right(), bottom(), Theme.alpha(Theme.ACCENT, 0x38));
            Theme.roundedOutline(graphics, getX(), getY(), right(), bottom(), Theme.alpha(Theme.ACCENT, 0xB0));
            textColor = Theme.ACCENT;
        } else if (isHoveredOrFocused()) {
            Theme.rounded(graphics, getX(), getY(), right(), bottom(), 0x18FFFFFF);
            textColor = Theme.TEXT;
        } else {
            textColor = Theme.MUTED;
        }
        Font font = Minecraft.getInstance().font;
        graphics.text(font, getMessage(), getX() + (getWidth() - font.width(getMessage())) / 2, getY() + 3, textColor);
    }

    // AbstractWidget has getRight() and getBottom() only since 1.20.2.
    private int right() {
        return getX() + getWidth();
    }

    private int bottom() {
        return getY() + getHeight();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
