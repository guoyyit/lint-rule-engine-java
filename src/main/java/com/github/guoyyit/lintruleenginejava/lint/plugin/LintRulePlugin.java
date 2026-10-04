package com.github.guoyyit.lintruleenginejava.lint.plugin;

import java.util.List;

/**
 * Plugin SPI. Register implementations via
 * {@code META-INF/services/com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin}
 * (Java {@link java.util.ServiceLoader}) or programmatically with
 * {@link PluginRegistry#of(LintRulePlugin...)}.
 *
 * <p>Plugins must declare their id, name, version and the rules they contribute.
 * Loading failures are isolated: a broken plugin never crashes the engine.
 */
public interface LintRulePlugin {

    /** Unique plugin id, e.g. {@code "builtin"}. */
    String getId();

    /** Human readable plugin name. */
    String getName();

    /** Plugin version; included in cache keys so upgrades invalidate caches. */
    String getVersion();

    /** The rules contributed by this plugin. */
    List<LintRule> getRules();
}
