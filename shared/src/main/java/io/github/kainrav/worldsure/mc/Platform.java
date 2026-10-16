package io.github.kainrav.worldsure.mc;

import io.github.kainrav.worldsure.ModInfo;

import java.nio.file.Path;
import java.util.List;

/** The few things that differ between loaders; each loader installs its implementation at startup. */
public interface Platform {
    String loaderName();

    Path gameDir();

    Path configDir();

    List<ModInfo> installedMods();

    static Platform get() {
        return Holder.instance;
    }

    static void set(Platform platform) {
        Holder.instance = platform;
    }

    final class Holder {
        private static Platform instance;

        private Holder() {
        }
    }
}
