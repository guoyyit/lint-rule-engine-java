package com.github.guoyyit.lintruleenginejava.plugin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.rule.Rule;

import java.util.List;
import java.util.Optional;

/**
 * Result of plugin loading: successfully loaded rules plus per-plugin failures.
 */
public interface PluginRegistry {

    /** All rules contributed by successfully loaded plugins. */
    List<Rule> rules();

    /** Find a rule by its fully qualified identity. */
    Optional<Rule> findRule(RuleId id);

    /** Failures collected while loading, in deterministic order. */
    List<PluginLoadFailure> failures();

    /** Names of successfully loaded plugins, sorted. */
    List<String> loadedPluginNames();
}
