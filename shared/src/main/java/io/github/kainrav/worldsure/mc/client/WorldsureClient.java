package io.github.kainrav.worldsure.mc.client;

import io.github.kainrav.worldsure.Report;
import io.github.kainrav.worldsure.Worldsure;
import io.github.kainrav.worldsure.WorldReview;
import io.github.kainrav.worldsure.mc.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
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
        Optional<WorldReview> review = Worldsure.review(dir, platform.configDir(), platform.installedMods());
        if (review.isEmpty() || !review.get().hasChanges()) {
            return false;
        }
        Minecraft.getInstance().gui.setScreen(WorldsureScreen.beforeLoad(review.get(), new Choices(dir, open, onCancel)));
        return true;
    }

    /** What the buttons of the screen before loading do. */
    record Choices(Path worldDir, Runnable open, Runnable cancel) {
        void backUpAndPlay(String worldName) {
            Minecraft minecraft = Minecraft.getInstance();
            // Paint a message first: the copy runs on this thread and can take a while for big worlds.
            minecraft.setScreenAndShow(new GenericMessageScreen(Component.translatable("worldsure.backing_up", worldName)));
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
                    Component.translatable("worldsure.backup_failed.title"),
                    Component.translatable("worldsure.backup_failed.question", backup.error()),
                    Component.translatable("worldsure.button.play_anyway"),
                    CommonComponents.GUI_CANCEL));
        }

        void play(Report.BackupResult backup) {
            Worldsure.rememberDecision(worldDir, backup);
            CONFIRMED.add(worldDir);
            open.run();
        }
    }

    /** The button that opens the Worldsure screen from the pause menu of a single player world. */
    public static Optional<AbstractWidget> pauseMenuButton(Screen screen) {
        if (!(screen instanceof PauseScreen pause) || !pause.showsPauseMenu()) {
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
                () -> Minecraft.getInstance().gui.setScreen(WorldsureScreen.inGame(review.get(), screen))));
    }
}
