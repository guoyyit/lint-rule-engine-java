package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.builtin.BuiltinPlugin;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoader;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.plugin.PluginSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The built-in plugin is discoverable through the ServiceLoader mechanism. */
class ServiceLoaderPluginTest {

    @Test
    void builtinPluginDiscoveredViaServiceLoader() {
        PluginRegistry registry = new PluginLoader().load(List.of(
                new PluginSource.ServiceLoader(
                        BuiltinPlugin.class.getClassLoader(), null)));

        assertTrue(registry.failures().isEmpty(), registry.failures()::toString);
        assertTrue(registry.loadedPluginNames().contains(BuiltinPlugin.NAME));
        assertTrue(registry.findRule(RuleId.of("builtin", "trailing-whitespace")).isPresent());
        assertTrue(registry.findRule(RuleId.of("builtin", "no-todo")).isPresent());
        assertEquals(2, registry.rules().size());
    }
}
