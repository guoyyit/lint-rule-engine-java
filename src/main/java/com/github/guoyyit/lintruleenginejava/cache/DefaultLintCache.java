package com.github.guoyyit.lintruleenginejava.cache;

import java.util.Optional;

/** Bounded LRU implementation of {@link LintCache}. */
public final class DefaultLintCache implements LintCache {

    public DefaultLintCache(int maxEntries) {
        // skeleton
    }

    @Override
    public Optional<FileResult> get(CacheKey key) {
        // skeleton
        return Optional.empty();
    }

    @Override
    public void put(CacheKey key, FileResult result) {
        // skeleton
    }

    @Override
    public void invalidateAll() {
        // skeleton
    }

    @Override
    public int size() {
        // skeleton
        return 0;
    }
}
