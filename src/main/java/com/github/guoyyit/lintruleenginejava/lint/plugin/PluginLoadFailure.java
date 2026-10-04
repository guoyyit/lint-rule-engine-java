package com.github.guoyyit.lintruleenginejava.lint.plugin;

/**
 * Describes a plugin that could not be loaded.
 *
 * @param pluginId  the plugin id if it could be determined, otherwise a descriptor
 * @param message   human readable failure reason
 * @param exception the exception that caused the failure (may be null)
 */
public record PluginLoadFailure(String pluginId, String message, Throwable exception) {

    @Override
    public String toString() {
        return "PluginLoadFailure[plugin=" + pluginId + ", reason=" + message + "]";
    }
}
