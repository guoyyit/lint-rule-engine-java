package com.github.guoyyit.lintruleenginejava.plugin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.util.Hashes;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable registry built by {@link PluginLoader}. */
final class DefaultPluginRegistry implements PluginRegistry {

    private final List<Rule> rules;
    private final Map<RuleId, Rule> byId;
    private final List<PluginLoadFailure> failures;
    private final List<String> pluginNames;

    DefaultPluginRegistry(Map<RuleId, Rule> byId, List<PluginLoadFailure> failures,
                          List<String> pluginNames) {
        this.byId = Map.copyOf(byId);
        this.rules = byId.values().stream()
                .sorted(Comparator.comparing(r -> r.id().toString()))
                .toList();
        this.failures = List.copyOf(failures);
        this.pluginNames = List.copyOf(pluginNames);
    }

    @Override
    public List<Rule> rules() {
        return rules;
    }

    @Override
    public Optional<Rule> findRule(RuleId id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<PluginLoadFailure> failures() {
        return failures;
    }

    @Override
    public List<String> loadedPluginNames() {
        return pluginNames;
    }

    /**
     * Stable fingerprint of the active rule set: identities, default
     * severities, scope globs and fix capability. Any change here invalidates
     * cached file results.
     */
    public String rulesFingerprint() {
        TreeMap<String, String> parts = new TreeMap<>();
        for (Rule r : rules) {
            parts.put(r.id().toString(),
                    r.defaultSeverity() + "|" + r.appliesTo() + "|" + r.canFix());
        }
        StringBuilder sb = new StringBuilder("rules:");
        parts.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
        return Hashes.sha256(sb.toString());
    }
}
