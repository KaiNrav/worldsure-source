package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.WorldsureConfig;
import io.github.kainrav.worldsure.mc.Platform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//#if >=26.1
import net.minecraft.client.gui.GuiGraphicsExtractor;
//#else
//$ import net.minecraft.client.gui.GuiGraphics;
//#endif

import java.io.IOException;
import java.util.function.Function;

/**
 * Worldsure's settings from config/worldsure.properties, one button each; every click saves the file.
 * Opened from the Worldsure screen and, on NeoForge and Forge, from the mod list.
 */
public final class SettingsScreen extends Screen {
    private static final int MAX_PANEL_WIDTH = 340;
    private static final int PAD = 10;
    private static final int MARGIN = 8;
    private static final int ROW_HEIGHT = 20;
    private static final int[] KEEP_CHOICES = {1, 3, 5, 10, 20};

    private final Screen parent;
    private WorldsureConfig config;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("worldsure.settings.title"));
        this.parent = parent;
        this.config = Worldsure.config(Platform.get().configDir());
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
        int y = MARGIN + PAD + 22;
        y = row(left, right, y, "worldsure.settings.auto_backup", c -> onOff(c.autoBackup()),
                c -> new WorldsureConfig(!c.autoBackup(), c.keepBackups(), c.serverOnly(), c.strict()), false);
        y = row(left, right, y, "worldsure.settings.keep_backups", c -> Component.literal(Integer.toString(c.keepBackups())),
                c -> new WorldsureConfig(c.autoBackup(), nextKeep(c.keepBackups()), c.serverOnly(), c.strict()), false);
        y = row(left, right, y, "worldsure.settings.server_only", c -> onOff(c.serverOnly()),
                c -> new WorldsureConfig(c.autoBackup(), c.keepBackups(), !c.serverOnly(), c.strict()), true);
        row(left, right, y, "worldsure.settings.strict", c -> onOff(c.strict()),
                c -> new WorldsureConfig(c.autoBackup(), c.keepBackups(), c.serverOnly(), !c.strict()), true);

        int bottom = height - MARGIN - PAD;
        addRenderableWidget(new FlatButton(left, bottom - ROW_HEIGHT, right - left, ROW_HEIGHT,
                Component.translatable("worldsure.button.done"), FlatButton.Style.PRIMARY, this::onClose));
    }

    /** One setting: a button showing its value that switches it and saves; with a hint line under it when asked. */
    private int row(int left, int right, int y, String key, Function<WorldsureConfig, Component> value,
                    Function<WorldsureConfig, WorldsureConfig> next, boolean hint) {
        FlatButton[] button = new FlatButton[1];
        button[0] = new FlatButton(left, y, right - left, ROW_HEIGHT, Component.translatable(key, value.apply(config)),
                FlatButton.Style.SECONDARY, () -> {
                    config = next.apply(config);
                    save();
                    button[0].setMessage(Component.translatable(key, value.apply(config)));
                });
        addRenderableWidget(button[0]);
        return y + ROW_HEIGHT + (hint ? 26 : 6);
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "worldsure.settings.on" : "worldsure.settings.off");
    }

    private static int nextKeep(int keep) {
        for (int choice : KEEP_CHOICES) {
            if (choice > keep) {
                return choice;
            }
        }
        return KEEP_CHOICES[0];
    }

    private void save() {
        try {
            config.save(Platform.get().configDir());
        } catch (IOException e) {
            Worldsure.LOG.error("Could not save {}", WorldsureConfig.FILE_NAME, e);
        }
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
        int width = panelWidth() - 2 * PAD;
        graphics.text(font, "⛨", left, y0 + PAD, Theme.ACCENT);
        graphics.text(font, title, left + 11, y0 + PAD, Theme.ACCENT);
        // Hints sit under the server-only and strict rows (rows 3 and 4).
        int rowsTop = y0 + PAD + 22;
        hint(graphics, "worldsure.settings.server_only.hint", left, rowsTop + 2 * (ROW_HEIGHT + 6) + ROW_HEIGHT + 3, width);
        hint(graphics, "worldsure.settings.strict.hint", left, rowsTop + 2 * (ROW_HEIGHT + 6) + ROW_HEIGHT + 26 + ROW_HEIGHT + 3, width);
    }

    private void hint(Gfx graphics, String key, int x, int y, int width) {
        String text = Component.translatable(key).getString();
        int line = 0;
        for (net.minecraft.util.FormattedCharSequence part : font.split(Component.literal(text), width)) {
            if (line == 2) {
                break;
            }
            StringBuilder plain = new StringBuilder();
            part.accept((index, style, codePoint) -> {
                plain.appendCodePoint(codePoint);
                return true;
            });
            graphics.text(font, plain.toString(), x, y + line * 10, Theme.MUTED);
            line++;
        }
    }

    @Override
    public void onClose() {
        WorldsureClient.setScreen(parent);
    }
}
