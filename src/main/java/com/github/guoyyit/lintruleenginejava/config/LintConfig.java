package com.github.guoyyit.lintruleenginejava.config;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Effective configuration for a directory (already merged along the tree).
 * Instances are immutable.
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

    public LintConfig {
        // TreeMap keyed by the rule's canonical string gives a stable iteration order.
        TreeMap<RuleId, Severity> m = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        if (ruleSeverity != null) {
            m.putAll(ruleSeverity);
        }
        ruleSeverity = Map.copyOf(m);
        disabledRules = disabledRules == null ? List.of()
                : disabledRules.stream().distinct().sorted((a, b) -> a.toString().compareTo(b.toString())).toList();
        globs = globs == null ? List.of() : List.copyOf(globs);
    }

    /** Empty configuration: every rule keeps its declared default severity. */
    public static LintConfig empty() {
        return new LintConfig(Map.of(), List.of(), List.of());
    }

    /**
     * Effective severity for a rule: override, else the supplied default.
     * Never returns {@code null}.
     */
    public Severity effectiveSeverity(RuleId id, Severity defaultSeverity) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        Severity s = ruleSeverity.get(id);
        return s != null ? s : (defaultSeverity != null ? defaultSeverity : Severity.WARNING);
    }

    public boolean isDisabled(RuleId id) {
        return id != null && disabledRules.contains(id);
    }
}
