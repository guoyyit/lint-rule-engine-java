package com.github.guoyyit.lintruleenginejava.engine;

import java.nio.file.Path;

/**
 * A per-file analysis failure (timeout, I/O error, rogue rule exception).
 * One file's failure never affects other files.
 */
public record CheckFailure(Path file, String reason, String causeClass) {

    @Override
    public String toString() {
        return file + ": " + reason + " (" + causeClass + ")";
    }
}
