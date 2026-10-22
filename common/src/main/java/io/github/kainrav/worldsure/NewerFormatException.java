package io.github.kainrav.worldsure;

import java.io.IOException;
import java.nio.file.Path;

/**
 * A Worldsure file was written by a newer Worldsure in a format this version cannot read.
 * Such a file is left as it is, so going back to the newer Worldsure loses nothing.
 */
public final class NewerFormatException extends IOException {
    private final int found;
    private final int known;

    public NewerFormatException(Path file, int found, int known) {
        super(file + " has format " + found + ", written by a newer Worldsure; this one reads formats up to " + known);
        this.found = found;
        this.known = known;
    }

    /** The format of the file. */
    public int found() {
        return found;
    }

    /** The newest format this version reads. */
    public int known() {
        return known;
    }
}
