package com.github.guoyyit.lintruleenginejava.lint.config;

import com.github.guoyyit.lintruleenginejava.lint.model.Severity;

/**
 * Configuration for a single rule.
 *
 * @param enabled  whether the rule is enabled in this scope (null = inherit)
 * @param severity severity override (null = inherit / rule default)
 */
public record RuleConfig(Boolean enabled, Severity severity) {

    public static RuleConfig on() {
        return new RuleConfig(Boolean.TRUE, null);
    }

    public static RuleConfig off() {
        return new RuleConfig(Boolean.FALSE, null);
    }

    public static RuleConfig severity(Severity severity) {
        return new RuleConfig(null, severity);
    }

    /**
     * Merges {@code child} on top of {@code parent}: child fields override
     * parent fields, null fields inherit the parent value.
     */
    public RuleConfig merge(RuleConfig parent) {
        Boolean mergedEnabled = this.enabled != null ? this.enabled : parent.enabled;
        Severity mergedSeverity = this.severity != null ? this.severity : parent.severity;
        return new RuleConfig(mergedEnabled, mergedSeverity);
    }

    public boolean isEnabled(boolean defaultValue) {
        return enabled != null ? enabled : defaultValue;
    }
}
