package com.github.guoyyit.lintruleenginejava.lint.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Holds the successfully loaded plugins and the failures encountered while loading.
 *
 * <p>Loading is isolated per plugin: a plugin whose constructor throws, whose
 * service declaration is broken or which contributes invalid/duplicate rules
 * is recorded as a {@link PluginLoadFailure} and skipped — it never aborts the
 * engine and all other plugins keep working.
 */
public final class PluginRegistry {

    private final List<LintRulePlugin> plugins;
    private final List<PluginLoadFailure> failures;

    private PluginRegistry(List<LintRulePlugin> plugins, List<PluginLoadFailure> failures) {
        this.plugins = List.copyOf(plugins);
        this.failures = List.copyOf(failures);
    }

    /** Loads plugins via the {@link ServiceLoader} mechanism. */
    public static PluginRegistry load() {
        List<LintRulePlugin> loaded = new ArrayList<>();
        List<PluginLoadFailure> failed = new ArrayList<>();
        ServiceLoader<LintRulePlugin> loader = ServiceLoader.load(LintRulePlugin.class);
        var iterator = loader.iterator();
        while (iterator.hasNext()) {
            LintRulePlugin plugin;
            try {
                plugin = iterator.next();
            } catch (Throwable t) {
                // ServiceConfigurationError or a constructor failure: the service
                // descriptor cannot even be instantiated.
                failed.add(new PluginLoadFailure("unknown",
                        "Failed to instantiate plugin declared in META-INF/services: " + t.getMessage(), t));
                continue;
            }
            validate(plugin, loaded, failed);
        }
        loaded.sort(Comparator.comparing(LintRulePlugin::getId));
        return new PluginRegistry(loaded, failed);
    }

    /** Creates an empty registry (no plugins, no failures). */
    public static PluginRegistry empty() {
        return new PluginRegistry(List.of(), List.of());
    }

    /**
     * Merges extra plugins into a base registry, validating them against the
     * rules already present. Failures from the base are preserved.
     */
    public static PluginRegistry combine(PluginRegistry base, List<LintRulePlugin> extra) {
        List<LintRulePlugin> loaded = new ArrayList<>(base.plugins);
        List<PluginLoadFailure> failed = new ArrayList<>(base.failures);
        for (LintRulePlugin plugin : extra) {
            validate(plugin, loaded, failed);
        }
        loaded.sort(Comparator.comparing(LintRulePlugin::getId));
        return new PluginRegistry(loaded, failed);
    }

    /** Creates a registry from explicitly supplied plugins (useful for tests). */
    public static PluginRegistry of(LintRulePlugin... plugins) {
        List<LintRulePlugin> loaded = new ArrayList<>();
        List<PluginLoadFailure> failed = new ArrayList<>();
        for (LintRulePlugin plugin : plugins) {
            validate(plugin, loaded, failed);
        }
        loaded.sort(Comparator.comparing(LintRulePlugin::getId));
        return new PluginRegistry(loaded, failed);
    }

    private static void validate(LintRulePlugin plugin,
                                 List<LintRulePlugin> loaded,
                                 List<PluginLoadFailure> failed) {
        String pluginId;
        try {
            pluginId = plugin.getId();
        } catch (Throwable t) {
            failed.add(new PluginLoadFailure("unknown",
                    "Plugin threw while declaring its id: " + t.getMessage(), t));
            return;
        }
        if (pluginId == null || pluginId.isBlank()) {
            failed.add(new PluginLoadFailure(String.valueOf(pluginId),
                    "Plugin declares a null or blank id; plugin skipped.", null));
            return;
        }
        try {
            List<LintRule> rules = plugin.getRules();
            if (rules == null) {
                failed.add(new PluginLoadFailure(pluginId,
                        "Plugin '" + pluginId + "' returned null rules; plugin skipped.", null));
                return;
            }
            Set<String> seen = new HashSet<>();
            for (LintRule rule : rules) {
                if (rule == null || rule.getId() == null || rule.getId().isBlank()) {
                    failed.add(new PluginLoadFailure(pluginId,
                            "Plugin '" + pluginId + "' contributes an invalid rule (null/blank id); plugin skipped.", null));
                    return;
                }
                if (!seen.add(rule.getId())) {
                    failed.add(new PluginLoadFailure(pluginId,
                            "Plugin '" + pluginId + "' contributes duplicate rule id '" + rule.getId()
                                    + "' within the plugin; plugin skipped.", null));
                    return;
                }
                for (LintRulePlugin existing : loaded) {
                    for (LintRule existingRule : existing.getRules()) {
                        if (existingRule.getId().equals(rule.getId())) {
                            failed.add(new PluginLoadFailure(pluginId,
                                    "Plugin '" + pluginId + "' contributes rule id '" + rule.getId()
                                            + "' which is already provided by plugin '" + existing.getId()
                                            + "'; plugin skipped.", null));
                            return;
                        }
                    }
                }
            }
            loaded.add(plugin);
        } catch (Throwable t) {
            failed.add(new PluginLoadFailure(pluginId,
                    "Plugin '" + pluginId + "' failed while loading its rules: " + t.getMessage(), t));
        }
    }

    public List<LintRulePlugin> getPlugins() {
        return plugins;
    }

    public List<PluginLoadFailure> getFailures() {
        return failures;
    }

    /** All rules of all loaded plugins, sorted by rule id for determinism. */
    public List<LintRule> getRules() {
        return plugins.stream()
                .flatMap(p -> p.getRules().stream())
                .sorted(Comparator.comparing(LintRule::getId))
                .toList();
    }
}
