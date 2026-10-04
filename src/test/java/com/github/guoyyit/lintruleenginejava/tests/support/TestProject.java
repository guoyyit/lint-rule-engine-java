package com.github.guoyyit.lintruleenginejava.tests.support;

import com.github.guoyyit.lintruleenginejava.builtin.BuiltinPlugin;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoader;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.plugin.PluginSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Creates throwaway project trees for engine tests. */
public final class TestProject implements AutoCloseable {

    public final Path root;

    private TestProject(Path root) {
        this.root = root;
    }

    public static TestProject create() {
        try {
            return new TestProject(Files.createTempDirectory("lint-engine-test-"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path write(String relativePath, String content) {
        Path file = root.resolve(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    public String read(String relativePath) {
        try {
            return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(root.resolve(relativePath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setLastModified(String relativePath, long millis) {
        try {
            Files.setLastModifiedTime(root.resolve(relativePath),
                    java.nio.file.attribute.FileTime.fromMillis(millis));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PluginRegistry builtinRegistry() {
        return new PluginLoader().load(List.of(
                new PluginSource.ClassNames(BuiltinPlugin.class.getClassLoader(),
                        List.of(BuiltinPlugin.class.getName()))));
    }

    public PluginRegistry registryOf(Class<?>... pluginClasses) {
        List<String> names = java.util.Arrays.stream(pluginClasses)
                .map(Class::getName).toList();
        return new PluginLoader().load(List.of(
                new PluginSource.ClassNames(getClass().getClassLoader(), names)));
    }

    @Override
    public void close() {
        deleteRecursively(root);
    }

    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort under temp dir cleanup
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
