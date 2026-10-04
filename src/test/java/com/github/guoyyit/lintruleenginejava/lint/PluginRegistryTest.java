package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginLoadFailure;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests plugin loading: built-in plugins load, broken plugins are isolated
 * and failures are locatable.
 */
class PluginRegistryTest {

    @Test
    void loadsBuiltinPluginsViaServiceLoader() {
        PluginRegistry registry = PluginRegistry.load();
        List<String> ids = registry.getPlugins().stream().map(LintRulePlugin::getId).toList();
        assertTrue(ids.contains("builtin-no-todo"), "built-in plugin should be loaded: " + ids);
        assertTrue(ids.contains("builtin-trailing-whitespace"));
        assertTrue(ids.contains("builtin-long-line"));
        assertTrue(ids.contains("builtin-no-tab-indent"));
        assertTrue(ids.contains("builtin-final-newline"));
        assertTrue(registry.getFailures().isEmpty(), "no failures expected: " + registry.getFailures());
        List<String> ruleIds = registry.getRules().stream().map(LintRule::getId).toList();
        assertEquals(ruleIds.stream().sorted().toList(), ruleIds, "rules must be sorted by id");
    }

    @Test
    void failingPluginIsIsolatedAndFailureIsLocatable() {
        PluginRegistry registry = PluginRegistry.of(new FailingPlugin());
        assertTrue(registry.getPlugins().isEmpty());
        assertEquals(1, registry.getFailures().size());
        PluginLoadFailure failure = registry.getFailures().get(0);
        assertEquals("test-failing", failure.pluginId());
        assertTrue(failure.message().contains("getRules"));
        assertTrue(failure.exception() instanceof IllegalStateException);
    }

    @Test
    void blankPluginIdIsReported() {
        PluginRegistry registry = PluginRegistry.of(FailingPlugin.blankId());
        assertTrue(registry.getPlugins().isEmpty());
        assertEquals(1, registry.getFailures().size());
        assertTrue(registry.getFailures().get(0).message().contains("blank id"));
    }

    @Test
    void duplicateRuleIdAcrossPluginsIsReportedAndSkipped() {
        LintRulePlugin first = new ConflictPlugin();
        LintRulePlugin duplicate = new LintRulePlugin() {
            @Override public String getId() { return "test-duplicate"; }
            @Override public String getName() { return "Duplicate"; }
            @Override public String getVersion() { return "1.0"; }
            @Override public List<LintRule> getRules() {
                return List.of(first.getRules().get(0)); // same rule id "conflict-a"
            }
        };
        PluginRegistry registry = PluginRegistry.of(first, duplicate);
        assertEquals(1, registry.getPlugins().size());
        assertEquals("test-conflict", registry.getPlugins().get(0).getId());
        assertEquals(1, registry.getFailures().size());
        assertTrue(registry.getFailures().get(0).message().contains("conflict-a"));
    }

    @Test
    void healthyPluginsSurviveAFailingOne() {
        PluginRegistry registry = PluginRegistry.of(new ConflictPlugin(), new FailingPlugin());
        assertEquals(1, registry.getPlugins().size());
        assertEquals("test-conflict", registry.getPlugins().get(0).getId());
        assertEquals(1, registry.getFailures().size());
        // The surviving plugin still contributes its rules.
        assertEquals(2, registry.getRules().size());
    }
}
