package com.github.guoyyit.lintruleenginejava.suppress;

import com.github.guoyyit.lintruleenginejava.model.RuleId;

import java.util.Optional;

/**
 * One parsed inline suppression directive.
 *
 * @param markerLine 1-based line carrying the marker comment
 * @param targetLine 1-based line whose diagnostics are suppressed
 * @param ruleId     referenced rule; empty for {@code lint:suppress-all}
 * @param rawText    original marker text, used in error messages
 */
public record Suppression(int markerLine, int targetLine, Optional<RuleId> ruleId, String rawText) {
}
