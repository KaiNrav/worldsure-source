package io.github.kainrav.worldsure.mc.client;

import com.mojang.blaze3d.Blaze3D;
import io.github.kainrav.worldsure.WorldBackup;
import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.WorldReview;
import io.github.kainrav.worldsure.mc.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows what changed in a world's mods. Opened before the world loads, where the player decides
 * whether to go on, and from the pause menu, to look again.
 */
public final class WorldsureScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 400;
    private static final int PAD = 10;
    private static final int MARGIN = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BANNER_HEIGHT = 18;

    private final WorldReview review;
    /** What the buttons do before loading; null when opened from the pause menu. */
    private final WorldsureClient.Choices choices;
    private final Runnable onBack;
    private boolean showAll;
    private ModChangeList list;

    private WorldsureScreen(WorldReview review, WorldsureClient.Choices choices, Runnable onBack) {
        super(Component.translatable("worldsure.screen.title"));
        this.review = review;
        this.choices = choices;
        this.onBack = onBack;
        this.showAll = !review.hasChanges();
    }

    static WorldsureScreen beforeLoad(WorldReview review, WorldsureClient.Choices choices) {
        return new WorldsureScreen(review, choices, choices.cancel());
    }

    static WorldsureScreen inGame(WorldReview review, Screen parent) {
        return new WorldsureScreen(review, null, () -> Minecraft.getInstance().gui.setScreen(parent));
    }

    private boolean beforeLoad() {
        return choices != null;
    }

    private int panelWidth() {
        return Math.min(width - 2 * MARGIN, MAX_PANEL_WIDTH);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    @Override
    protected void init() {
        int left = panelLeft() + PAD;
        int right = panelLeft() + panelWidth() - PAD;
        int top = MARGIN + PAD;
        int bottom = height - MARGIN - PAD;

        int tabsY = top + 26;
        TabButton changesTab = new TabButton(left, tabsY,
                Component.translatable("worldsure.tab.changes", review.changes().size()),
                () -> !showAll, () -> select(false));
        TabButton allTab = new TabButton(changesTab.getRight() + 4, tabsY,
                Component.translatable("worldsure.tab.all", review.entries().size()),
                () -> showAll, () -> select(true));
        addRenderableWidget(changesTab);
        addRenderableWidget(allTab);

        int buttonsY = bottom - BUTTON_HEIGHT;
        int listTop = tabsY + 20;
        int listBottom = buttonsY - BANNER_HEIGHT - 12;
        list = new ModChangeList(minecraft, left, listTop, right - left, Math.max(listBottom - listTop, 26), beforeLoad());
        addRenderableWidget(list);
        showEntries();

        List<FlatButton> buttons = new ArrayList<>();
        if (beforeLoad()) {
            buttons.add(button("worldsure.button.back", FlatButton.Style.SECONDARY, onBack));
            switch (review.backup().state()) {
                case WILL_BACKUP -> {
                    buttons.add(button("worldsure.button.play_without_backup", FlatButton.Style.SECONDARY,
                            () -> choices.play(Report.BackupResult.SKIPPED)));
                    buttons.add(button("worldsure.button.backup_and_play", FlatButton.Style.PRIMARY,
                            () -> choices.backUpAndPlay(review.worldName())));
                }
                case DISABLED -> buttons.add(button("worldsure.button.play_without_backup", FlatButton.Style.DANGER,
                        () -> choices.play(Report.BackupResult.DISABLED)));
                default -> buttons.add(button("worldsure.button.play", FlatButton.Style.PRIMARY,
                        () -> choices.play(Report.BackupResult.NOT_NEEDED)));
            }
        } else {
            Path backups = WorldBackup.backupsDir(Platform.get().gameDir());
            FlatButton openBackups = button("worldsure.button.backups", FlatButton.Style.SECONDARY,
                    () -> Blaze3D.openPath(backups));
            openBackups.active = Files.isDirectory(backups);
            buttons.add(openBackups);
            buttons.add(button("worldsure.button.done", FlatButton.Style.PRIMARY, onBack));
        }
        int gap = 6;
        int buttonWidth = (right - left - gap * (buttons.size() - 1)) / buttons.size();
        for (int i = 0; i < buttons.size(); i++) {
            FlatButton button = buttons.get(i);
            button.setX(left + i * (buttonWidth + gap));
            button.setY(buttonsY);
            button.setWidth(buttonWidth);
            addRenderableWidget(button);
        }
    }

    private static FlatButton button(String key, FlatButton.Style style, Runnable onPress) {
        return new FlatButton(0, 0, 0, BUTTON_HEIGHT, Component.translatable(key), style, onPress);
    }

    private void select(boolean all) {
        showAll = all;
        showEntries();
    }

    private void showEntries() {
        list.show(showAll ? review.entries() : review.changes());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int x0 = panelLeft();
        int x1 = x0 + panelWidth();
        int y0 = MARGIN;
        int y1 = height - MARGIN;
        Theme.rounded(graphics, x0, y0, x1, y1, Theme.PANEL);
        Theme.roundedOutline(graphics, x0, y0, x1, y1, Theme.PANEL_BORDER);
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 2, Theme.ACCENT);

        int left = x0 + PAD;
        int right = x1 - PAD;
        int top = y0 + PAD;
        extractHeader(graphics, left, right, top);
        extractCounters(graphics, right, top + 29, mouseX, mouseY);
        extractBanner(graphics, left, right, height - MARGIN - PAD - BUTTON_HEIGHT - BANNER_HEIGHT - 6);

        super.extractRenderState(graphics, mouseX, mouseY, a);

        if (list.children().isEmpty()) {
            Component empty = Component.translatable("worldsure.empty");
            graphics.text(font, empty, (list.getX() + list.getRight() - font.width(empty)) / 2, list.getY() + 12,
                    Theme.MUTED, false);
        }
    }

    private void extractHeader(GuiGraphicsExtractor graphics, int left, int right, int top) {
        graphics.text(font, "⛨", left, top, Theme.ACCENT, false);
        graphics.text(font, title, left + 11, top, Theme.ACCENT, false);
        int nameX = left + 11 + font.width(title) + 6;
        graphics.text(font, "·", nameX - 4, top, Theme.DIM, false);
        String name = font.plainSubstrByWidth(review.worldName(), right - nameX - 2);
        graphics.text(font, name, nameX + 2, top, Theme.TEXT, false);

        Component subtitle;
        if (review.backup().state() == WorldReview.BackupState.FIRST_VISIT) {
            subtitle = Component.translatable("worldsure.screen.first_visit");
        } else if (!review.hasChanges()) {
            subtitle = Component.translatable("worldsure.screen.unchanged", savedAt());
        } else {
            subtitle = Component.translatable(beforeLoad() ? "worldsure.screen.changed.before" : "worldsure.screen.changed.after",
                    savedAt());
        }
        String text = font.plainSubstrByWidth(subtitle.getString(), right - left);
        graphics.text(font, text, left, top + 12, Theme.MUTED, false);
    }

    /** Compact "✖ 2  ↓ 1  ↑ 1  + 1" counters, right-aligned on the tab row. */
    private void extractCounters(GuiGraphicsExtractor graphics, int right, int y, int mouseX, int mouseY) {
        int x = right;
        int x1 = right;
        WorldReview.Status[] order = {WorldReview.Status.ADDED, WorldReview.Status.UPDATED,
                WorldReview.Status.DOWNGRADED, WorldReview.Status.REMOVED};
        for (WorldReview.Status status : order) {
            long count = review.count(status);
            if (count == 0) {
                continue;
            }
            String text = Theme.glyph(status) + " " + count;
            int w = font.width(text) + 8;
            x -= w;
            Theme.rounded(graphics, x, y - 3, x + w, y + 11, Theme.alpha(Theme.color(status), 0x30));
            graphics.text(font, text, x + 4, y, Theme.color(status), false);
            x -= 4;
        }
        if (x != right && mouseX >= x && mouseX < x1 && mouseY >= y - 3 && mouseY < y + 11) {
            graphics.setTooltipForNextFrame(font, Component.translatable("worldsure.counters",
                    review.count(WorldReview.Status.REMOVED), review.count(WorldReview.Status.DOWNGRADED),
                    review.count(WorldReview.Status.UPDATED), review.count(WorldReview.Status.ADDED)), mouseX, mouseY);
        }
    }

    /** One line about the backup, tinted by how safe the world is. */
    private void extractBanner(GuiGraphicsExtractor graphics, int left, int right, int y) {
        WorldReview.Backup backup = review.backup();
        int color;
        String icon;
        Component text;
        switch (backup.state()) {
            case WILL_BACKUP -> {
                color = Theme.GREEN;
                icon = "⛨";
                text = Component.translatable("worldsure.backup.will", backup.keep());
            }
            case CREATED -> {
                color = Theme.GREEN;
                icon = "✔";
                text = Component.translatable("worldsure.backup.created", Path.of(backup.detail()).getFileName().toString());
            }
            case FAILED -> {
                color = Theme.RED;
                icon = "⚠";
                text = Component.translatable("worldsure.backup.failed", backup.detail());
            }
            case DISABLED -> {
                color = Theme.ORANGE;
                icon = "⚠";
                text = Component.translatable(beforeLoad() ? "worldsure.backup.disabled" : "worldsure.backup.disabled.after");
            }
            case SKIPPED -> {
                color = Theme.ORANGE;
                icon = "⚠";
                text = Component.translatable("worldsure.backup.skipped");
            }
            case FIRST_VISIT -> {
                color = Theme.ACCENT;
                icon = "ℹ";
                text = Component.translatable("worldsure.backup.first_visit");
            }
            default -> {
                color = Theme.GREY;
                icon = "ℹ";
                text = Component.translatable(review.hasChanges() ? "worldsure.backup.not_needed" : "worldsure.backup.unchanged");
            }
        }
        Theme.rounded(graphics, left, y, right, y + BANNER_HEIGHT, Theme.alpha(color, 0x22));
        graphics.fill(left, y + 2, left + 2, y + BANNER_HEIGHT - 2, color);
        graphics.text(font, icon, left + 8, y + 5, color, false);
        String line = font.plainSubstrByWidth(text.getString(), right - left - 26);
        graphics.text(font, line, left + 20, y + 5, Theme.TEXT, false);
    }

    private String savedAt() {
        if (review.savedAt() == null) {
            return "?";
        }
        try {
            String code = Minecraft.getInstance().getLanguageManager().getSelected();
            Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
            return OffsetDateTime.parse(review.savedAt())
                    .atZoneSameInstant(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale));
        } catch (DateTimeParseException e) {
            return review.savedAt();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onBack.run();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        onBack.run();
    }
}
