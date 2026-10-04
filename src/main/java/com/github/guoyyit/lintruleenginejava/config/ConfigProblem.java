package com.github.guoyyit.lintruleenginejava.config;

import java.nio.file.Path;

/**
 * A locate-able problem found while parsing a configuration file.
 *
 * @param configFile absolute path of the offending config file
 * @param line       1-based line in that file
 * @param message    explanation
 */
public record ConfigProblem(Path configFile, int line, String message) {

    @Override
    public String toString() {
        return configFile + ":" + line + ": " + message;
    }
}
