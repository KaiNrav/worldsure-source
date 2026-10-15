package io.github.kainrav.worldsure;

import java.util.Objects;

/** One installed mod as the world passport records it. */
public record ModInfo(String id, String version) {
    public ModInfo {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
    }
}
