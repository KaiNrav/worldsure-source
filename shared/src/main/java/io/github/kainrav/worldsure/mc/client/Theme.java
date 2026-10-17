package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.WorldReview;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Colours (ARGB) and the small drawing helpers the Worldsure screen is built from. */
final class Theme {
    static final int PANEL = 0xF0101319;
    static final int PANEL_BORDER = 0xFF262C38;
    static final int SURFACE = 0x80000000;
    static final int ROW_HOVER = 0x1AFFFFFF;
    static final int ACCENT = 0xFF5CC8F5;
    static final int TEXT = 0xFFE8ECF2;
    static final int MUTED = 0xFF8E97A8;
    static final int DIM = 0xFF5B6372;

    static final int RED = 0xFFFF6464;
    static final int ORANGE = 0xFFFFA040;
    static final int YELLOW = 0xFFF2D04B;
    static final int GREEN = 0xFF4FDB86;
    static final int GREY = 0xFF6E7685;

    private Theme() {
    }

    static int color(WorldReview.Status status) {
        return switch (status) {
            case REMOVED -> RED;
            case DOWNGRADED -> ORANGE;
            case UPDATED -> YELLOW;
            case ADDED -> GREEN;
            case UNCHANGED -> GREY;
        };
    }

    static String glyph(WorldReview.Status status) {
        return switch (status) {
            case REMOVED -> "✖";
            case DOWNGRADED -> "↓";
            case UPDATED -> "↑";
            case ADDED -> "+";
            case UNCHANGED -> "•";
        };
    }

    static Component label(WorldReview.Status status) {
        return Component.translatable("worldsure.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** The same colour at a different opacity, 0-255. */
    static int alpha(int color, int alpha) {
        return (alpha << 24) | (color & 0xFFFFFF);
    }

    /** A rectangle with its corners cut by one pixel, which reads as rounded at GUI scale. */
    static void rounded(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0 + 1, y0, x1 - 1, y1, color);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    /** A one pixel border following {@link #rounded}. */
    static void roundedOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.horizontalLine(x0 + 1, x1 - 2, y0, color);
        graphics.horizontalLine(x0 + 1, x1 - 2, y1 - 1, color);
        graphics.verticalLine(x0, y0, y1 - 1, color);
        graphics.verticalLine(x1 - 1, y0, y1 - 1, color);
    }
}
