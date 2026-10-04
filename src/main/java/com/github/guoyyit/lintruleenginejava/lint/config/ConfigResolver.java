package com.github.guoyyit.lintruleenginejava.lint.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the effective {@link LintConfig} for a file by walking the directory
 * chain from the root down to the file's directory, merging each scope config
 * on top of its parent.
 *
 * <p>Loaded configs are cached per directory and invalidated when the config
 * file's modification time changes, so long-running runs pick up config edits
 * without a restart.
 */
public final class ConfigResolver {

    private final Path root;
    private final LintConfig globalConfig;
    private final Map<Path, CachedConfig> cache = new LinkedHashMap<>();

    private record CachedConfig(long lastModified, LintConfig config) {
    }

    public ConfigResolver(Path root, LintConfig globalConfig) {
        this.root = root.toAbsolutePath().normalize();
        this.globalConfig = globalConfig == null ? LintConfig.EMPTY : globalConfig;
    }

    /**
     * Resolves the config applying to {@code file}.
     *
     * @param file absolute or root-relative file path
     */
    public LintConfig resolve(Path file) {
        Path absolute = file.isAbsolute() ? file.normalize() : root.resolve(file).normalize();
        LintConfig merged = globalConfig;
        Path current = root;
        merged = LintConfig.merge(merged, loadCached(current));
        Path parent = absolute.getParent();
        if (parent != null && parent.startsWith(root)) {
            for (Path segment : root.relativize(parent)) {
                current = current.resolve(segment);
                merged = LintConfig.merge(merged, loadCached(current));
            }
        }
        return merged;
    }

    private LintConfig loadCached(Path directory) {
        try {
            Path configFile = directory.resolve(ConfigLoader.CONFIG_FILE_NAME);
            long mtime = Files.isRegularFile(configFile) ? Files.getLastModifiedTime(configFile).toMillis() : -1L;
            synchronized (cache) {
                CachedConfig cached = cache.get(directory);
                if (cached != null && cached.lastModified == mtime) {
                    return cached.config;
                }
            }
            Optional<LintConfig> loaded = ConfigLoader.loadInDirectory(directory);
            LintConfig config = loaded.orElse(LintConfig.EMPTY);
            synchronized (cache) {
                cache.put(directory, new CachedConfig(mtime, config));
            }
            return config;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
