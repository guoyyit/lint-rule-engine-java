package com.github.guoyyit.lintruleenginejava.config;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Resolves the effective configuration for files by merging {@code .lintrc}
 * files from the analysis root down to the file's parent directory:
 * child entries inherit from, then override, parent entries.
 */
public final class ConfigResolver {

    public static final String CONFIG_FILE_NAME = ".lintrc";

    public ConfigResolver(Path root) {
        // skeleton
    }

    /** Effective configuration for the given relative file path. */
    public LintConfig resolve(Path relativeFile) {
        // skeleton
        return null;
    }

    /**
     * Parse one config file into a raw (unmerged) config layer.
     * Invalid references (unknown rule / bad severity) are returned as
     * parse problems rather than thrown.
     */
    public static ParsedConfig parse(Path configFile) throws IOException {
        // skeleton
        return null;
    }

    /** Stable hash covering all config inputs that may influence results. */
    public String fingerprint(Path relativeFile) {
        // skeleton
        return null;
    }
}
