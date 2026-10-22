package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.WorldReview;
import io.github.kainrav.worldsure.mc.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
//#if >=1.20.5
import net.minecraft.client.gui.screens.GenericMessageScreen;
//#endif
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The client side of Worldsure: the screen before a world opens and the pause menu button. */
public final class WorldsureClient {
    /** Worlds the player already confirmed, so reopening them skips the screen once. */
    private static final Set<Path> CONFIRMED = ConcurrentHashMap.newKeySet();

    private WorldsureClient() {
    }

    /**
     * Called when the player opens a single player world. Shows the Worldsure screen instead when the
     * world's mods changed.
     *
     * @param open opens the world for real; called again once the player confirms
     * @return true when the screen was shown and opening the world must wait for the player
     */
    public static boolean interceptOpen(Path worldDir, Runnable open, Runnable onCancel) {
        Path dir = worldDir.toAbsolutePath().normalize();
        if (CONFIRMED.remove(dir)) {
            return false;
        }
        Platform platform = Platform.get();
        // A backup picked while the world was open is put in place now, before anything reads the world.
        Worldsure.restorePending(dir, platform.gameDir());
        Optional<WorldReview> review = Worldsure.review(dir, platform.configDir(), platform.installedMods());
        if (review.isEmpty() || !review.get().hasChanges()) {
            return false;
        }
        setScreen(WorldsureScreen.beforeLoad(review.get(), new Choices(dir, open, onCancel)));
        return true;
    }

    /** What the buttons of the screen before loading do. */
    record Choices(Path worldDir, Runnable open, Runnable cancel) {
        void backUpAndPlay(String worldName) {
            // Paint a message first: the copy runs on this thread and can take a while for big worlds.
            Component message = Component.translatable("worldsure.backing_up", worldName);
            //#if >=1.21.11
            Minecraft.getInstance().setScreenAndShow(new GenericMessageScreen(message));
            //#elif >=1.20.5
            //$ Minecraft.getInstance().forceSetScreen(new GenericMessageScreen(message));
            //#else
            //$ Minecraft.getInstance().forceSetScreen(new net.minecraft.client.gui.screens.GenericDirtMessageScreen(message));
            //#endif
            Platform platform = Platform.get();
            Report.BackupResult backup = Worldsure.backup(worldDir, platform.gameDir(), platform.configDir());
            if (backup.status() != Report.BackupResult.Status.FAILED) {
                play(backup);
                return;
            }
            setScreen(new ConfirmScreen(
                    playAnyway -> {
                        if (playAnyway) {
                            play(backup);
                        } else {
                            cancel.run();
                        }
                    },
                    Component.translatable("worldsure.backup_failed.title"),
                    Component.translatable("worldsure.backup_failed.question", backup.error()),
                    Component.translatable("worldsure.button.play_anyway"),
                    CommonComponents.GUI_CANCEL));
        }

        /** After a backup was restored: shows the screen again for the restored world, or opens it. */
        void reopen() {
            if (!interceptOpen(worldDir, open, cancel)) {
                open.run();
            }
        }

        void play(Report.BackupResult backup) {
            Worldsure.rememberDecision(worldDir, backup);
            CONFIRMED.add(worldDir);
            open.run();
        }
    }

    /** Shows a screen: since 26.2 through the GUI, before that through Minecraft itself. */
    public static void setScreen(Screen screen) {
        //#if >=26.2
        Minecraft.getInstance().gui.setScreen(screen);
        //#else
        //$ Minecraft.getInstance().setScreen(screen);
        //#endif
    }

    /** Opens a folder in the system file manager. */
    static void openFolder(Path dir) {
        //#if >=26.3
        com.mojang.blaze3d.Blaze3D.openPath(dir);
        //#elif >=1.21.11
        //$ net.minecraft.util.Util.getPlatform().openPath(dir);
        //#elif >=1.20.2
        //$ net.minecraft.Util.getPlatform().openPath(dir);
        //#else
        //$ net.minecraft.Util.getPlatform().openFile(dir.toFile());
        //#endif
    }

    /** False for the bare pause screen of F3+Esc. */
    private static boolean showsPauseMenu(PauseScreen pause) {
        //#if >=1.20.2
        return pause.showsPauseMenu();
        //#else
        //$ // 1.20.1 keeps the flag private; without the menu the screen holds only its title.
        //$ return pause.children().size() > 1;
        //#endif
    }

    /** The button that opens the Worldsure screen from the pause menu of a single player world. */
    public static Optional<AbstractWidget> pauseMenuButton(Screen screen) {
        if (!(screen instanceof PauseScreen pause) || !showsPauseMenu(pause)) {
            return Optional.empty();
        }
        Optional<WorldReview> review = Worldsure.currentReview();
        if (review.isEmpty()) {
            return Optional.empty();
        }
        int changes = review.get().changes().size();
        Component label = changes > 0
                ? Component.translatable("worldsure.button.pause.changes", changes)
                : Component.translatable("worldsure.button.pause");
        FlatButton.Style style = changes > 0 ? FlatButton.Style.WARNING : FlatButton.Style.SECONDARY;
        int width = Minecraft.getInstance().font.width(label) + 20;
        return Optional.of(new FlatButton(8, screen.height - 28, width, 20, label, style,
                () -> setScreen(WorldsureScreen.inGame(review.get(), screen))));
    }
}
