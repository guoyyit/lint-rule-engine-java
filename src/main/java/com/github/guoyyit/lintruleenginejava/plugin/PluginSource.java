package com.github.guoyyit.lintruleenginejava.plugin;

import java.nio.file.Path;
import java.util.List;

/**
 * A discoverable source of plugin implementations.
 */
public sealed interface PluginSource permits PluginSource.ClassNames, PluginSource.ServiceLoader {

    /**
     * Explicit plugin class names resolved through a given class loader.
     */
    record ClassNames(ClassLoader classLoader, List<String> classNames) implements PluginSource {
        public ClassNames {
            if (classLoader == null) {
                throw new IllegalArgumentException("classLoader must not be null");
            }
            classNames = List.copyOf(classNames);
        }
    }

    /**
     * Plugins discovered through {@link java.util.ServiceLoader}.
     */
    record ServiceLoader(ClassLoader classLoader, Path location) implements PluginSource {
        public ServiceLoader {
            if (classLoader == null) {
                throw new IllegalArgumentException("classLoader must not be null");
            }
        }
    }
}
