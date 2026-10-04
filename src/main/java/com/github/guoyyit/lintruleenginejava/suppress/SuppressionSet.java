package com.github.guoyyit.lintruleenginejava.suppress;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.RuleId;

import java.util.List;

/**
 * Suppression directives and marker problems parsed from a single file.
 * Inline markers are recognized inside {@code //} or {@code #} comments:
 * <ul>
 *   <li>{@code // lint:suppress rule-name} on the same line, or on the
 *       line immediately above the diagnostic's line;</li>
 *   <li>{@code // lint:suppress-all} for every rule, same scope rules;</li>
 *   <li>{@code lint:disable} / {@code lint:enable} for a file region.</li>
 * </ul>
 */
public interface SuppressionSet {

    boolean isSuppressed(RuleId ruleId, int line);

    /** Diagnostics describing malformed markers or unknown rule names. */
    List<Diagnostic> markerProblems();
}
