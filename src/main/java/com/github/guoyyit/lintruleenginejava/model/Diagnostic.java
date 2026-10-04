package com.github.guoyyit.lintruleenginejava.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * A single finding produced by a rule for one file.
 *
 * @param ruleId    producing rule
 * @param file      source file, relative to the analysis root
 * @param line      1-based line number
 * @param column    1-based column number
 * @param severity  effective severity (after configuration override)
 * @param message   human-readable message
 * @param fix       optional automatic fix
 */
public record Diagnostic(
        RuleId ruleId,
        Path file,
        int line,
        int column,
        Severity severity,
        String message,
        Fix fix
) implements Comparable<Diagnostic> {

    public Diagnostic {
        if (ruleId == null) {
            throw new IllegalArgumentException("ruleId must not be null");
        }
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1");
        }
        if (column < 1) {
            column = 1;
        }
        if (severity == null) {
            severity = Severity.WARNING;
        }
        if (message == null) {
            message = "";
        }
    }

    public Optional<Fix> optionalFix() {
        return Optional.ofNullable(fix);
    }

    /** Stable, order- and concurrency-independent ordering. */
    @Override
    public int compareTo(Diagnostic o) {
        int c = this.file.toString().compareTo(o.file.toString());
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.line, o.line);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.column, o.column);
        if (c != 0) {
            return c;
        }
        c = this.ruleId.toString().compareTo(o.ruleId.toString());
        if (c != 0) {
            return c;
        }
        return this.message.compareTo(o.message);
    }
}
