package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.WorldBackup;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.WorldsureCommands;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$ import net.minecraft.client.gui.GuiGraphics;
//#endif

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

/**
 * The automatic backups of one world, newest first; picking one restores it after a confirmation.
 * Before the world loads the backup is restored at once. While the world is open it cannot be
 * swapped, so the restore waits until the world is opened again.
 */
public final class BackupsScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 320;
    private static final int PAD = 10;
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 4;

    private final Screen parent;
    private final Path worldDir;
    /** Restores at once and carries on; null while the world is open. */
    private final Runnable restoreNow;
    private List<WorldBackup.Backup> backups = List.of();

    private BackupsScreen(Screen parent, Path worldDir, Runnable restoreNow) {
        super(Component.translatable("worldsure.backups.title", worldDir.getFileName().toString()));
        this.parent = parent;
        this.worldDir = worldDir;
        this.restoreNow = restoreNow;
    }

    /** Before the world loads; {@code afterRestore} reopens it once a backup was put in place. */
    static BackupsScreen beforeLoad(Screen parent, Path worldDir, Runnable afterRestore) {
        return new BackupsScreen(parent, worldDir, afterRestore);
    }

    /** While the world is open. */
    static BackupsScreen inGame(Screen parent, Path worldDir) {
        return new BackupsScreen(parent, worldDir, null);
    }

    private int panelWidth() {
        return Math.min(width - 2 * MARGIN, MAX_PANEL_WIDTH);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    @Override
    protected void init() {
        Path gameDir = Platform.get().gameDir();
        try {
            backups = WorldBackup.list(gameDir, worldDir.getFileName().toString());
        } catch (IOException e) {
            Worldsure.LOG.warn("Could not list the backups", e);
            backups = List.of();
        }
        int left = panelLeft() + PAD;
        int right = panelLeft() + panelWidth() - PAD;
        int bottom = height - MARGIN - PAD;
        int y = MARGIN + PAD + 28;
        int lastRowY = bottom - ROW_HEIGHT - 10 - ROW_HEIGHT;
        DateTimeFormatter time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale());
        for (WorldBackup.Backup backup : backups) {
            if (y > lastRowY) {
                break;
            }
            Component label = Component.translatable("worldsure.backups.row", time.format(backup.time()),
                    WorldsureCommands.size(backup.size()));
            addRenderableWidget(new FlatButton(left, y, right - left, ROW_HEIGHT, label, FlatButton.Style.SECONDARY,
                    () -> confirm(backup)));
            y += ROW_HEIGHT + ROW_GAP;
        }

        Path folder = WorldBackup.backupsDir(gameDir);
        int half = (right - left - 6) / 2;
        FlatButton open = new FlatButton(left, bottom - ROW_HEIGHT, half, ROW_HEIGHT,
                Component.translatable("worldsure.button.open_folder"), FlatButton.Style.SECONDARY,
                () -> WorldsureClient.openFolder(folder));
        open.active = Files.isDirectory(folder);
        addRenderableWidget(open);
        addRenderableWidget(new FlatButton(left + half + 6, bottom - ROW_HEIGHT, right - left - half - 6, ROW_HEIGHT,
                Component.translatable("worldsure.button.back"), FlatButton.Style.PRIMARY, this::onClose));
    }

    private static Locale locale() {
        String code = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        return Locale.forLanguageTag(code.replace('_', '-'));
    }

    private void confirm(WorldBackup.Backup backup) {
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale()).format(backup.time());
        WorldsureClient.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                restore(backup);
            } else {
                WorldsureClient.setScreen(this);
            }
        }, Component.translatable("worldsure.restore.title"),
                Component.translatable(restoreNow != null ? "worldsure.restore.question.now" : "worldsure.restore.question.later", when)));
    }

    private void restore(WorldBackup.Backup backup) {
        Path gameDir = Platform.get().gameDir();
        String world = worldDir.getFileName().toString();
        try {
            WorldBackup.requestRestore(gameDir, world, backup.name());
        } catch (IOException e) {
            failed(e.getMessage());
            return;
        }
        if (restoreNow == null) {
            WorldsureClient.setScreen(new AlertScreen(() -> WorldsureClient.setScreen(parent),
                    Component.translatable("worldsure.restore.title"), Component.translatable("worldsure.restore.later")));
            return;
        }
        String result = Worldsure.restorePending(worldDir, gameDir).orElse("");
        if (result.startsWith("Restored")) {
            restoreNow.run();
        } else {
            failed(result);
        }
    }

    private void failed(String why) {
        WorldsureClient.setScreen(new AlertScreen(() -> WorldsureClient.setScreen(this),
                Component.translatable("worldsure.restore.title"), Component.translatable("worldsure.restore.failed", why)));
    }

    //#if >=26.1
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        extractPanel(new Gfx(graphics));
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }
    //#elif >=1.21.6
    //$ @Override
    //$ public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
    //$     extractPanel(new Gfx(graphics));
    //$     super.render(graphics, mouseX, mouseY, a);
    //$ }
    //#elif >=1.20.2
    //$ @Override
    //$ public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
    //$     super.renderBackground(graphics, mouseX, mouseY, a);
    //$     extractPanel(new Gfx(graphics));
    //$ }
    //#else
    //$ @Override
    //$ public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
    //$     renderBackground(graphics);
    //$     extractPanel(new Gfx(graphics));
    //$     super.render(graphics, mouseX, mouseY, a);
    //$ }
    //#endif

    private void extractPanel(Gfx graphics) {
        int x0 = panelLeft();
        int x1 = x0 + panelWidth();
        int y0 = MARGIN;
        int y1 = height - MARGIN;
        Theme.rounded(graphics, x0, y0, x1, y1, Theme.PANEL);
        Theme.roundedOutline(graphics, x0, y0, x1, y1, Theme.PANEL_BORDER);
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 2, Theme.ACCENT);
        int left = x0 + PAD;
        int top = y0 + PAD;
        graphics.text(font, "⛨", left, top, Theme.ACCENT);
        graphics.text(font, title, left + 11, top, Theme.ACCENT);
        Component hint = Component.translatable(backups.isEmpty() ? "worldsure.backups.empty" : "worldsure.backups.hint");
        graphics.text(font, font.plainSubstrByWidth(hint.getString(), panelWidth() - 2 * PAD), left, top + 13, Theme.MUTED);
    }

    @Override
    public void onClose() {
        WorldsureClient.setScreen(parent);
    }
}
