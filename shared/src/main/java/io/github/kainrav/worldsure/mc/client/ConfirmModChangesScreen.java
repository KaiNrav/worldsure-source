package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.mc.Platform;
import io.github.kainrav.worldsure.mc.WorldsureMc;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lists what changed in a world's mods before it loads and asks what to do:
 * back up and play (the default), play without a backup, or go back.
 */
public class ConfirmModChangesScreen extends ConfirmScreen {
    /** Worlds the player already confirmed, so reopening them skips this screen once. */
    private static final Set<Path> CONFIRMED = ConcurrentHashMap.newKeySet();

    private final Worldsure.Check check;
    private final Path worldDir;
    private final Runnable proceed;
    private final Runnable cancel;

    public ConfirmModChangesScreen(Worldsure.Check check, Path worldDir, Runnable proceed, Runnable cancel) {
        super(confirmed -> cancel.run(),
                Component.literal("The mods of \"" + check.worldName() + "\" changed").withStyle(ChatFormatting.GOLD),
                WorldsureMc.toComponent(check.lines()));
        this.check = check;
        this.worldDir = worldDir;
        this.proceed = proceed;
        this.cancel = cancel;
    }

    public static boolean consumeConfirmed(Path worldDir) {
        return CONFIRMED.remove(worldDir);
    }

    @Override
    protected LayoutElement addMessage() {
        return new MultiLineTextWidget(this.message, this.font).setMaxWidth(this.width - 50).setMaxRows(20);
    }

    @Override
    protected void addAdditionalText() {
        String hint = check.diff().isRisky()
                ? "Blocks of removed mods are kept as placeholders and come back with the mod."
                : "Only new mods were added; nothing in the world is at risk.";
        this.layout.addChild(new MultiLineTextWidget(Component.literal(hint).withStyle(ChatFormatting.GRAY), this.font)
                .setMaxWidth(this.width - 50).setCentered(true));
    }

    @Override
    protected void addButtons(LinearLayout buttons) {
        this.yesButton = buttons.addChild(Button.builder(Component.literal("Back up and play"), b -> backupAndPlay()).build());
        buttons.addChild(Button.builder(Component.literal("Play without backup"), b -> play(Report.BackupResult.SKIPPED)).build());
        this.noButton = buttons.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> cancel.run()).build());
    }

    private void backupAndPlay() {
        Minecraft minecraft = Minecraft.getInstance();
        // Paint a message first: the copy runs on this thread and can take a while for big worlds.
        minecraft.setScreenAndShow(new GenericMessageScreen(Component.literal("Backing up \"" + check.worldName() + "\"...")));
        Platform platform = Platform.get();
        Report.BackupResult backup = Worldsure.backup(worldDir, platform.gameDir(), platform.configDir());
        if (backup.status() != Report.BackupResult.Status.FAILED) {
            play(backup);
            return;
        }
        minecraft.gui.setScreen(new ConfirmScreen(
                playAnyway -> {
                    if (playAnyway) {
                        play(backup);
                    } else {
                        cancel.run();
                    }
                },
                Component.literal("The backup failed").withStyle(ChatFormatting.RED),
                Component.literal(backup.error() + "\nPlay anyway?"),
                Component.literal("Play anyway"),
                CommonComponents.GUI_CANCEL));
    }

    private void play(Report.BackupResult backup) {
        Worldsure.rememberDecision(worldDir, backup);
        CONFIRMED.add(worldDir);
        proceed.run();
    }
}
