package com.github.guoyyit.lintruleenginejava.lint.config;

import com.github.guoyyit.lintruleenginejava.lint.model.Severity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A lint configuration for a directory scope.
 *
 * @param rules   per-rule configuration keyed by rule id
 * @param include glob patterns of files to check (empty = all)
 * @param exclude glob patterns of files to skip
 */
public record LintConfig(Map<String, RuleConfig> rules,
                         Set<String> include,
                         Set<String> exclude) {

    public static final LintConfig EMPTY = new LintConfig(Map.of(), Set.of(), Set.of());

    public LintConfig {
        rules = Map.copyOf(rules);
        include = Set.copyOf(include);
        exclude = Set.copyOf(exclude);
    }

    /**
     * Merges {@code child} on top of {@code parent}. Rule configs are merged
     * rule by rule (child fields override parent fields); include/exclude sets
     * are unioned so child directories can only add patterns.
     */
    public static LintConfig merge(LintConfig parent, LintConfig child) {
        Map<String, RuleConfig> mergedRules = new LinkedHashMap<>(parent.rules);
        child.rules.forEach((ruleId, childRule) ->
                mergedRules.merge(ruleId, childRule, (p, c) -> c.merge(p)));
        Set<String> mergedInclude = Stream.concat(parent.include.stream(), child.include.stream())
                .collect(Collectors.toUnmodifiableSet());
        Set<String> mergedExclude = Stream.concat(parent.exclude.stream(), child.exclude.stream())
                .collect(Collectors.toUnmodifiableSet());
        return new LintConfig(mergedRules, mergedInclude, mergedExclude);
    }

    public Optional<RuleConfig> ruleConfig(String ruleId) {
        return Optional.ofNullable(rules.get(ruleId));
    }

    /** Resolves the effective severity for a rule in this scope. */
    public Severity effectiveSeverity(String ruleId, Severity defaultValue) {
        return ruleConfig(ruleId).map(RuleConfig::severity).orElse(defaultValue);
    }

    /** Whether the rule is enabled in this scope (default: enabled). */
    public boolean isEnabled(String ruleId) {
        return ruleConfig(ruleId).map(rc -> rc.isEnabled(true)).orElse(true);
    }
}
