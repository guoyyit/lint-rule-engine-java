package com.github.guoyyit.lintruleenginejava.plugin;

/**
 * Describes how a plugin was discovered, used to attribute load failures.
 */
public record PluginDescriptor(String name, String source) {
}
