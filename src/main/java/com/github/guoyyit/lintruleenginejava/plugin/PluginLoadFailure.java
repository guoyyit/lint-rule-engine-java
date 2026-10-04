package com.github.guoyyit.lintruleenginejava.plugin;

/**
 * A failed plugin-loading attempt. The failure is isolated: other plugins
 * still load and run.
 *
 * @param descriptor descriptor of the failing plugin, may carry a best-effort name
 * @param reason     short, human-readable cause
 * @param causeClass original exception class name, may be {@code null}
 */
public record PluginLoadFailure(PluginDescriptor descriptor, String reason, String causeClass) {

    public String location() {
        return descriptor == null ? "<unknown>" : descriptor.source();
    }
}
