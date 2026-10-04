package com.github.guoyyit.lintruleenginejava.plugin;

import com.github.guoyyit.lintruleenginejava.rule.Rule;

import java.util.List;

/**
 * A plugin contributes a named, self-describing set of rules.
 * Implementations must provide a public no-argument constructor so that
 * plugin loaders can instantiate them reflectively.
 */
public interface LintPlugin {

    /** Unique plugin name (case-insensitive); {@code null} or blank is invalid. */
    String name();

    /** Rules contributed by this plugin. */
    List<Rule> rules();
}
