package com.github.guoyyit.lintruleenginejava.cache;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bounded LRU implementation of {@link LintCache}.
 *
 * <p>Eviction is least-recently-used (access-ordered), so long-running
 * processes cannot grow without bound. All operations are synchronized;
 * cache keys already encode content hash, configuration fingerprint and
 * rule-set fingerprint, so changed inputs naturally miss.
 */
public final class DefaultLintCache implements LintCache {

    public static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final int maxEntries;
    private final LinkedHashMap<CacheKey, FileResult> map;

    public DefaultLintCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public DefaultLintCache(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be >= 1");
        }
        this.maxEntries = maxEntries;
        this.map = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<CacheKey, FileResult> eldest) {
                return size() > DefaultLintCache.this.maxEntries;
            }
        };
    }

    @Override
    public synchronized Optional<FileResult> get(CacheKey key) {
        return Optional.ofNullable(map.get(key));
    }

    @Override
    public synchronized void put(CacheKey key, FileResult result) {
        if (key == null || result == null) {
            throw new IllegalArgumentException("key/result must not be null");
        }
        map.put(key, result);
    }

    @Override
    public synchronized void invalidateAll() {
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }
}
