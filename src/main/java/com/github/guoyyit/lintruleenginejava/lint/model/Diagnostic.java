package com.github.guoyyit.lintruleenginejava.lint.model;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * A single diagnostic produced by a rule.
 *
 * @param category lint diagnostic or a hint about a malformed/unknown suppression marker
 */
public record Diagnostic(Category category,
                         String ruleId,
                         Severity severity,
                         Path filePath,
                         Range range,
                         String message,
                         Fix fix) implements Comparable<Diagnostic> {

    public enum Category { LINT, SUPPRESSION_HINT }

    public static final Comparator<Diagnostic> ORDER = Comparator
            .comparing((Diagnostic d) -> d.filePath == null ? "" : d.filePath.toString())
            .thenComparing(d -> d.range.start())
            .thenComparing(d -> d.ruleId)
            .thenComparing(d -> d.message);

    public static Diagnostic lint(String ruleId, Severity severity, Path filePath,
                                  Range range, String message, Fix fix) {
        return new Diagnostic(Category.LINT, ruleId, severity, filePath, range, message, fix);
    }

    public static Diagnostic suppressionHint(String ruleId, Severity severity, Path filePath,
                                             Range range, String message) {
        return new Diagnostic(Category.SUPPRESSION_HINT, ruleId, severity, filePath, range, message, null);
    }

    public Optional<Fix> fixOpt() {
        return Optional.ofNullable(fix);
    }

    @Override
    public int compareTo(Diagnostic other) {
        return ORDER.compare(this, other);
    }
}
