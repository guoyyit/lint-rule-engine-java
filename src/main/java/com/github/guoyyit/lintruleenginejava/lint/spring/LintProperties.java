package com.github.guoyyit.lintruleenginejava.lint.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuration properties for the lint engine.
 */
@ConfigurationProperties(prefix = "lint")
public class LintProperties {

    /** Root directory for resolving per-directory configs. */
    private Path root = Path.of(".");

    /** Whether to load plugins via ServiceLoader. */
    private boolean serviceLoaderEnabled = true;

    /** Maximum number of cached file results. */
    private int cacheSize = 1000;

    /** Worker thread count. */
    private int threads = Math.max(2, Runtime.getRuntime().availableProcessors());

    /** Per-file check timeout in milliseconds. */
    private long perFileTimeoutMillis = 60_000;

    public Path getRoot() { return root; }
    public void setRoot(Path root) { this.root = root; }
    public boolean isServiceLoaderEnabled() { return serviceLoaderEnabled; }
    public void setServiceLoaderEnabled(boolean serviceLoaderEnabled) { this.serviceLoaderEnabled = serviceLoaderEnabled; }
    public int getCacheSize() { return cacheSize; }
    public void setCacheSize(int cacheSize) { this.cacheSize = cacheSize; }
    public int getThreads() { return threads; }
    public void setThreads(int threads) { this.threads = threads; }
    public long getPerFileTimeoutMillis() { return perFileTimeoutMillis; }
    public void setPerFileTimeoutMillis(long perFileTimeoutMillis) { this.perFileTimeoutMillis = perFileTimeoutMillis; }
}
