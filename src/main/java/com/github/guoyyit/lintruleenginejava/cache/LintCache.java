package com.github.guoyyit.lintruleenginejava.cache;

import java.util.Optional;

/**
 * Bounded, concurrency-safe result cache. Entries are evicted in
 * least-recently-used order so long-running processes cannot grow without
 * bound; a rule-set/config change naturally produces a different key.
 */
public interface LintCache {

    Optional<FileResult> get(CacheKey key);

    void put(CacheKey key, FileResult result);

    /** Drop all entries (used when the rule set is replaced). */
    void invalidateAll();

    int size();
}
