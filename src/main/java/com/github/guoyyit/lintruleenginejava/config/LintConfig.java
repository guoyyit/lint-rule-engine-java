package com.github.guoyyit.lintruleenginejava.config;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

import java.util.List;
import java.util.Map;

/**
 * Effective configuration for a directory (already merged along the tree).
 *
 * @param ruleSeverity effective per-rule severities
 * @param disabledRules fully qualified rules disabled for this scope
 * @param globs         effective file include globs (empty means all files)
 */
public record LintConfig(
        Map<RuleId, Severity> ruleSeverity,
        List<RuleId> disabledRules,
        List<String> globs
) {

    /** Empty configuration: every rule keeps its declared default severity. */
    public static LintConfig empty() {
        // skeleton
        return null;
    }

    /** Effective severity for a rule: override, else the supplied default. */
    public Severity effectiveSeverity(RuleId id, Severity defaultSeverity) {
        // skeleton
        return null;
    }

    public boolean isDisabled(RuleId id) {
        // skeleton
        return false;
    }
}
