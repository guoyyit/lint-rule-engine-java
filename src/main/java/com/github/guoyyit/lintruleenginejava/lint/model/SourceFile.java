package com.github.guoyyit.lintruleenginejava.lint.model;

import java.nio.file.Path;

/**
 * A source file to be checked: its path and current content.
 */
public record SourceFile(Path path, String content) {
}
