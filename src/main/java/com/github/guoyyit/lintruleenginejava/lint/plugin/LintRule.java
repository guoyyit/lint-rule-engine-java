package com.github.guoyyit.lintruleenginejava.lint.plugin;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Fix;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A single lint rule contributed by a {@link LintRulePlugin}.
 *
 * <p>Rules must be deterministic: given the same {@link SourceFile} they must
 * always produce the same diagnostics. They must not mutate the file.
 */
public interface LintRule {

    /** Unique rule id, e.g. {@code "no-todo"}. Used in configs and suppression markers. */
    String getId();

    /** Human readable rule name. */
    String getName();

    /** Description of what the rule checks. */
    String getDescription();

    /** Default severity when no config overrides it. */
    default com.github.guoyyit.lintruleenginejava.lint.model.Severity getDefaultSeverity() {
        return com.github.guoyyit.lintruleenginejava.lint.model.Severity.WARNING;
    }

    /**
     * File extensions this rule applies to, e.g. {@code Set.of("java", "txt")}.
     * An empty set means the rule applies to every file.
     */
    default Set<String> getApplicableExtensions() {
        return Set.of();
    }

    /** Whether this rule can provide an automatic fix. */
    default boolean isFixable() {
        return false;
    }

    /**
     * Checks the given file and returns diagnostics. Must be deterministic and
     * must not throw for malformed input (the engine still guards exceptions).
     */
    List<Diagnostic> check(SourceFile file);

    /**
     * Produces a fix for the given diagnostic, if the rule supports fixing it.
     */
    default Optional<Fix> fix(SourceFile file, Diagnostic diagnostic) {
        return Optional.empty();
    }
}
