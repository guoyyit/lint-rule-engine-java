package com.github.guoyyit.lintruleenginejava.model;

/**
 * Fully qualified identity of a rule: plugin name plus rule name.
 * Equality is case-insensitive on both parts.
 */
public record RuleId(String plugin, String rule) {

    public RuleId {
        if (plugin == null || plugin.isBlank()) {
            throw new IllegalArgumentException("plugin must not be blank");
        }
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("rule must not be blank");
        }
        plugin = plugin.trim().toLowerCase();
        rule = rule.trim().toLowerCase();
    }

    public static RuleId of(String plugin, String rule) {
        return new RuleId(plugin, rule);
    }

    @Override
    public String toString() {
        return plugin + ":" + rule;
    }
}
