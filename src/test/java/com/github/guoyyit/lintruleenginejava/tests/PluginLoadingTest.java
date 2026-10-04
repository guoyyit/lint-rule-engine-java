package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoader;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoadFailure;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.plugin.PluginSource;
import com.github.guoyyit.lintruleenginejava.tests.fixtures.TestPlugins;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 1: failed plugins never crash loading; causes are locatable. */
class PluginLoadingTest {

    private PluginSource classes(String... names) {
        return new PluginSource.ClassNames(LintPlugin.class.getClassLoader(), List.of(names));
    }

    @Test
    void loadsHealthyPlugins() {
        PluginRegistry registry = new PluginLoader().load(List.of(
                classes(TestPlugins.GoodPlugin.class.getName())));

        assertTrue(registry.failures().isEmpty());
        assertEquals(List.of("good"), registry.loadedPluginNames());
        assertEquals(1, registry.rules().size());
    }

    @Test
    void missingClassIsReportedButDoesNotStopOtherPlugins() {
        PluginRegistry registry = new PluginLoader().load(List.of(classes(
                "com.example.does.not.Exist",
                TestPlugins.GoodPlugin.class.getName())));

        assertEquals(1, registry.failures().size());
        PluginLoadFailure failure = registry.failures().get(0);
        assertTrue(failure.location().contains("com.example.does.not.Exist"),
                "failure must locate the offending plugin source: " + failure.location());
        assertEquals(ClassNotFoundException.class.getName(), failure.causeClass());
        // The healthy plugin still loads and contributes its rule.
        assertEquals(1, registry.rules().size());
        assertTrue(registry.findRule(TestPlugins.GoodPlugin.RULE).isPresent());
    }

    @Test
    void constructorThrowingIsIsolated() {
        PluginRegistry registry = new PluginLoader().load(List.of(classes(
                TestPlugins.ExplodingPlugin.class.getName(),
                TestPlugins.GoodPlugin.class.getName())));

        assertEquals(1, registry.failures().size());
        PluginLoadFailure failure = registry.failures().get(0);
        assertTrue(failure.reason().contains("boom from constructor"),
                "root cause must be visible: " + failure.reason());
        assertEquals(IllegalStateException.class.getName(), failure.causeClass());
        assertEquals(1, registry.rules().size());
    }

    @Test
    void blankNameAndRulesThrowingAndWrongRulePrefixAreFailures() {
        PluginRegistry registry = new PluginLoader().load(List.of(classes(
                TestPlugins.BlankNamePlugin.class.getName(),
                TestPlugins.RulesThrowingPlugin.class.getName(),
                TestPlugins.WrongRulePlugin.class.getName())));

        assertEquals(3, registry.failures().size());
        assertEquals(0, registry.rules().size());
        // Every failure identifies a concrete source location.
        for (PluginLoadFailure f : registry.failures()) {
            assertFalse(f.location().isBlank());
            assertFalse(f.reason().isBlank());
        }
    }

    @Test
    void classNotImplementingPluginInterfaceIsReported() {
        PluginRegistry registry = new PluginLoader().load(List.of(classes(
                "java.lang.String")));

        assertEquals(1, registry.failures().size());
        assertTrue(registry.failures().get(0).reason().contains("does not implement LintPlugin"));
    }

    @Test
    void duplicateRuleIdsAreIgnoredDeterministically() {
        PluginRegistry registry = new PluginLoader().load(List.of(classes(
                TestPlugins.GoodPlugin.class.getName(),
                TestPlugins.GoodPlugin.class.getName())));

        assertEquals(1, registry.rules().size());
        assertEquals(1, registry.failures().size());
        assertTrue(registry.failures().get(0).reason().contains("duplicate rule id"));
    }
}
