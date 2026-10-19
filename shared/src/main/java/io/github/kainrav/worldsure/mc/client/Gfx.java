package io.github.kainrav.worldsure.mc.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.List;
//#if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$ import net.minecraft.client.gui.GuiGraphics;
//#endif
//#if <1.21.6
//$ import net.minecraft.client.Minecraft;
//$ import net.minecraft.client.gui.screens.Screen;
//$ import net.minecraft.util.FormattedCharSequence;
//#endif

/**
 * The drawing calls the Worldsure screen is made of, over the graphics type of this Minecraft:
 * GuiGraphicsExtractor since 26.1, GuiGraphics before.
 */
final class Gfx {
    //#if >=26.1
    private final GuiGraphicsExtractor graphics;

    Gfx(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }
    //#else
    //$ private final GuiGraphics graphics;
    //$
    //$ Gfx(GuiGraphics graphics) {
    //$     this.graphics = graphics;
    //$ }
    //#endif

    void fill(int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y1, color);
    }

    void horizontalLine(int x0, int x1, int y, int color) {
        //#if >=26.1
        graphics.horizontalLine(x0, x1, y, color);
        //#else
        //$ graphics.hLine(x0, x1, y, color);
        //#endif
    }

    void verticalLine(int x, int y0, int y1, int color) {
        //#if >=26.1
        graphics.verticalLine(x, y0, y1, color);
        //#else
        //$ graphics.vLine(x, y0, y1, color);
        //#endif
    }

    /** Text without a drop shadow. */
    void text(Font font, String text, int x, int y, int color) {
        //#if >=26.1
        graphics.text(font, text, x, y, color, false);
        //#else
        //$ graphics.drawString(font, text, x, y, color, false);
        //#endif
    }

    void text(Font font, Component text, int x, int y, int color) {
        //#if >=26.1
        graphics.text(font, text, x, y, color, false);
        //#else
        //$ graphics.drawString(font, text, x, y, color, false);
        //#endif
    }

    /** A tooltip drawn over everything else once the frame is done. */
    void tooltip(Font font, List<Component> lines, int x, int y) {
        //#if >=1.21.6
        graphics.setComponentTooltipForNextFrame(font, lines, x, y);
        //#else
        //$ Screen screen = Minecraft.getInstance().screen;
        //$ if (screen != null) {
        //$     List<FormattedCharSequence> text = lines.stream().map(Component::getVisualOrderText).toList();
        //$     screen.setTooltipForNextRenderPass(text);
        //$ }
        //#endif
    }
}
