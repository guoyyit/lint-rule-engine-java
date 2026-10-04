package com.github.guoyyit.lintruleenginejava.lint.model;

import java.util.List;

/**
 * An auto-fix for a diagnostic, consisting of one or more text edits.
 */
public record Fix(String description, List<TextEdit> edits) {
    public Fix {
        edits = List.copyOf(edits);
    }
}
