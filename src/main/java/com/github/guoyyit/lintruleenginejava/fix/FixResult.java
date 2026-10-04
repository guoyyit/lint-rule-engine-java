package com.github.guoyyit.lintruleenginejava.fix;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;

import java.nio.file.Path;
import java.util.List;

/**
 * Outcome of applying automatic fixes to one file.
 */
public record FixResult(
        Path file,
        String newContent,
        List<AppliedFix> appliedFixes,
        List<FixConflict> conflicts,
        boolean changed
) {

    /** One fix that was applied; fixes are applied deterministically. */
    public record AppliedFix(RuleId ruleId, Fix fix) {
    }

    /**
     * Two or more fixes that could not all be applied. A deterministic
     * policy ({@link FixPolicy}) decides the winner; losers are reported.
     */
    public record FixConflict(Fix winner, RuleId winnerRule, List<RuleId> rejectedRules, String reason) {
    }

    /** Diagnostics carrying a fix whose fix could not be applied. */
    public List<Diagnostic> unapplied(List<Diagnostic> all) {
        java.util.Set<Fix> appliedFixSet = appliedFixes().stream()
                .map(af -> af.fix())
                .collect(java.util.stream.Collectors.toSet());
        if (all == null) {
            return List.of();
        }
        return all.stream()
                .filter(d -> d.fix() != null && !appliedFixSet.contains(d.fix()))
                .toList();
    }
}
