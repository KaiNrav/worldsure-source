package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.ObjectCounts;
import io.github.kainrav.worldsure.WorldReview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
//#if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$ import net.minecraft.client.gui.GuiGraphics;
//#endif

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The scrolling list of mods: one card per mod, coloured by what happened to it. */
final class ModChangeList extends ObjectSelectionList<ModChangeList.Row> {
    private static final int ROW_HEIGHT = 26;

    private final boolean beforeLoad;

    ModChangeList(Minecraft minecraft, int x, int y, int width, int height, boolean beforeLoad) {
        //#if >=1.20.2
        super(minecraft, width, height, y, ROW_HEIGHT);
        setX(x);
        //#else
        //$ super(minecraft, width, height, y, y + height, ROW_HEIGHT);
        //$ setLeftPos(x);
        //$ // The dirt background and the top and bottom shadows are drawn by renderBackground below instead.
        //$ setRenderBackground(false);
        //$ setRenderTopAndBottom(false);
        //#endif
        this.beforeLoad = beforeLoad;
    }

    // Until 1.20.2 the list is no widget and keeps its bounds in x0, y0, x1, y1.
    int listLeft() {
        //#if >=1.20.2
        return getX();
        //#else
        //$ return x0;
        //#endif
    }

    int listTop() {
        //#if >=1.20.2
        return getY();
        //#else
        //$ return y0;
        //#endif
    }

    int listRight() {
        //#if >=1.20.2
        return getRight();
        //#else
        //$ return x1;
        //#endif
    }

    int listBottom() {
        //#if >=1.20.2
        return getBottom();
        //#else
        //$ return y1;
        //#endif
    }

    void show(List<WorldReview.Entry> entries) {
        replaceEntries(entries.stream().map(Row::new).toList());
        setScrollAmount(0);
    }

    @Override
    public int getRowWidth() {
        return listRight() - listLeft() - 14;
    }
    //#if <1.20.2

    //$ @Override
    //$ protected int getScrollbarPosition() {
    //$     // 1.20.1 puts the scrollbar relative to the screen centre, which suits only full width lists.
    //$     return listRight() - 6;
    //$ }
    //#endif

    //#if >=1.21.9
    @Override
    protected boolean entriesCanBeSelected() {
        return false;
    }
    //#else
    //$ @Override
    //$ public void setSelected(Row row) {
    //$     // Cards are not selectable.
    //$ }
    //#endif

    //#if >=26.1
    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
        Theme.rounded(new Gfx(graphics), getX(), getY(), getRight(), getBottom(), Theme.SURFACE);
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }
    //#elif >=1.20.5
    //$ @Override
    //$ protected void renderListBackground(GuiGraphics graphics) {
    //$     Theme.rounded(new Gfx(graphics), getX(), getY(), getRight(), getBottom(), Theme.SURFACE);
    //$ }
    //$
    //$ @Override
    //$ protected void renderListSeparators(GuiGraphics graphics) {
    //$ }
    //#else
    //$ @Override
    //$ protected void renderBackground(GuiGraphics graphics) {
    //$     Theme.rounded(new Gfx(graphics), listLeft(), listTop(), listRight(), listBottom(), Theme.SURFACE);
    //$ }
    //#endif

    final class Row extends ObjectSelectionList.Entry<Row> {
        private final WorldReview.Entry entry;

        Row(WorldReview.Entry entry) {
            this.entry = entry;
        }

        @Override
        public Component getNarration() {
            return Component.literal(entry.id() + ", ").append(Theme.label(entry.status()));
        }

        //#if >=26.1
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            draw(new Gfx(graphics), getContentX(), getContentY(), getContentRight(), getContentBottom(), mouseX, mouseY, hovered);
        }
        //#elif >=1.21.9
        //$ @Override
        //$ public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float a) {
        //$     draw(new Gfx(graphics), getContentX(), getContentY(), getContentRight(), getContentBottom(), mouseX, mouseY, hovered);
        //$ }
        //#else
        //$ @Override
        //$ public void render(GuiGraphics graphics, int index, int top, int left, int width, int height,
        //$                    int mouseX, int mouseY, boolean hovered, float a) {
        //$     draw(new Gfx(graphics), left, top, left + width, top + height, mouseX, mouseY, hovered);
        //$ }
        //#endif

        private void draw(Gfx graphics, int x0, int y0, int x1, int y1, int mouseX, int mouseY, boolean hovered) {
            Font font = minecraft.font;
            int color = Theme.color(entry.status());
            boolean changed = entry.status() != WorldReview.Status.UNCHANGED;

            Theme.rounded(graphics, x0, y0, x1, y1, hovered ? Theme.ROW_HOVER : 0x0CFFFFFF);
            graphics.fill(x0, y0 + 2, x0 + 2, y1 - 2, changed ? color : Theme.alpha(color, 0x60));

            // Status badge.
            int bx = x0 + 6;
            int by = y0 + (y1 - y0 - 16) / 2;
            Theme.rounded(graphics, bx, by, bx + 16, by + 16, Theme.alpha(color, changed ? 0x40 : 0x20));
            String glyph = Theme.glyph(entry.status());
            graphics.text(font, glyph, bx + 8 - font.width(glyph) / 2, by + 4, color);

            // Status label on the right of the first line.
            Component label = Theme.label(entry.status());
            int labelWidth = font.width(label);
            int textX = bx + 22;
            int labelX = x1 - 6 - labelWidth;
            if (changed) {
                graphics.text(font, label, labelX, y0 + 3, color);
            }

            String id = font.plainSubstrByWidth(entry.id(), labelX - textX - 6);
            graphics.text(font, id, textX, y0 + 3, changed ? Theme.TEXT : Theme.MUTED);

            int chipsLeft = extractCounts(graphics, font, x1 - 6, y0 + 12, color, changed);

            Component detail = detail();
            String detailText = detail.getString();
            int detailWidth = chipsLeft - 6 - textX;
            String shown = font.plainSubstrByWidth(detailText, detailWidth);
            boolean truncated = shown.length() < detailText.length();
            if (truncated) {
                shown = font.plainSubstrByWidth(detailText, detailWidth - font.width("…")) + "…";
            }
            graphics.text(font, shown, textX, y0 + 13, changed ? Theme.MUTED : Theme.DIM);

            boolean counted = !entry.counts().isZero();
            if (hovered && (truncated || counted || !id.equals(entry.id()))) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Theme.colored(Component.literal(entry.id()), color));
                tooltip.add(detail);
                if (counted) {
                    tooltip.add(Theme.colored(Component.translatable("worldsure.counts.full", exact(entry.counts().blocks()),
                            exact(entry.counts().blockEntities()), exact(entry.counts().entities())), Theme.MUTED));
                }
                if (entry.status() == WorldReview.Status.REMOVED && counted) {
                    tooltip.add(Theme.colored(Component.translatable("worldsure.counts.removed_hint"), Theme.ORANGE));
                }
                graphics.tooltip(font, tooltip, mouseX, mouseY);
            }
        }

        /**
         * Small chips with how many blocks, chests or machines and mobs of the mod the world holds,
         * right-aligned at {@code right}.
         *
         * @return the left edge of the chips, or {@code right} when there are none
         */
        private int extractCounts(Gfx graphics, Font font, int right, int y, int color, boolean changed) {
            ObjectCounts counts = entry.counts();
            if (counts.isZero()) {
                if (entry.status() != WorldReview.Status.REMOVED) {
                    return right;
                }
                Component none = Component.translatable("worldsure.counts.none");
                int x = right - font.width(none);
                graphics.text(font, none, x, y + 1, Theme.DIM);
                return x;
            }
            int x = right;
            long[] values = {counts.entities(), counts.blockEntities(), counts.blocks()};
            String[] glyphs = {"☻", "⚙", "■"};
            for (int i = 0; i < values.length; i++) {
                if (values[i] == 0) {
                    continue;
                }
                String text = glyphs[i] + " " + compact(values[i]);
                int w = font.width(text) + 6;
                x -= w;
                Theme.rounded(graphics, x, y - 1, x + w, y + 10, changed ? Theme.alpha(color, 0x26) : 0x14FFFFFF);
                graphics.text(font, text, x + 3, y + 1, changed ? Theme.TEXT : Theme.MUTED);
                x -= 3;
            }
            return x;
        }

        private Component detail() {
            return switch (entry.status()) {
                case REMOVED -> Component.translatable(
                        beforeLoad ? "worldsure.detail.removed" : "worldsure.detail.removed.after", entry.oldVersion());
                case DOWNGRADED -> Component.translatable("worldsure.detail.downgraded",
                        entry.oldVersion(), entry.newVersion());
                case UPDATED -> Component.translatable("worldsure.detail.updated", entry.oldVersion(), entry.newVersion());
                case ADDED -> Component.translatable("worldsure.detail.added", entry.newVersion());
                case UNCHANGED -> Component.literal(entry.newVersion());
            };
        }
    }

    /** 950, 1.2k, 90k, 3.4M: short enough for a chip. */
    static String compact(long value) {
        if (value < 1_000) {
            return Long.toString(value);
        }
        if (value < 1_000_000) {
            return value < 10_000 ? String.format(Locale.ROOT, "%.1fk", value / 1_000.0) : value / 1_000 + "k";
        }
        return value < 10_000_000 ? String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0) : value / 1_000_000 + "M";
    }

    private static String exact(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
