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

/** A flat, filled button in the Worldsure colours instead of the stone texture. */
final class FlatButton extends AbstractButton {
    enum Style {
        // Light text on deep fills: dark pixel-font text on a bright fill is hard to read.
        PRIMARY(0xFF1F6FB2, 0xFFFFFFFF),
        DANGER(0xFFB3363A, 0xFFFFFFFF),
        WARNING(0xFF3A2E12, Theme.YELLOW),
        SECONDARY(0xFF262C38, Theme.TEXT);

        final int fill;
        final int text;

        Style(int fill, int text) {
            this.fill = fill;
            this.text = text;
        }
    }

    private final Style style;
    private final Runnable onPress;

    FlatButton(int x, int y, int width, int height, Component message, Style style, Runnable onPress) {
        super(x, y, width, height, message);
        this.style = style;
        this.onPress = onPress;
    }

    //#if >=1.21.9
    @Override
    public void onPress(InputWithModifiers input) {
        onPress.run();
    }
    //#else
    //$ @Override
    //$ public void onPress() {
    //$     onPress.run();
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
        int x0 = getX();
        int y0 = getY();
        int x1 = right();
        int y1 = bottom();
        int fill = !active ? Theme.alpha(style.fill, 0x60) : style.fill;
        Theme.rounded(graphics, x0, y0, x1, y1, fill);
        if (style == Style.PRIMARY || style == Style.DANGER) {
            graphics.horizontalLine(x0 + 1, x1 - 2, y0, 0x40FFFFFF);
        }
        if (active && isHoveredOrFocused()) {
            Theme.rounded(graphics, x0, y0, x1, y1, 0x30FFFFFF);
            Theme.roundedOutline(graphics, x0, y0, x1, y1, 0xA0FFFFFF);
        }
        Font font = Minecraft.getInstance().font;
        int textColor = active ? style.text : Theme.alpha(style.text, 0x80);
        String text = font.plainSubstrByWidth(getMessage().getString(), getWidth() - 8);
        // No drop shadow: it smears dark text on the light primary button.
        graphics.text(font, text, (x0 + x1 - font.width(text)) / 2, y0 + (getHeight() - 8) / 2, textColor);
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
