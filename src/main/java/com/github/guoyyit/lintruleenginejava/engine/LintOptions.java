package com.github.guoyyit.lintruleenginejava.engine;

import com.github.guoyyit.lintruleenginejava.cache.DefaultLintCache;
import com.github.guoyyit.lintruleenginejava.cache.LintCache;
import com.github.guoyyit.lintruleenginejava.fix.FixPolicy;

/** Engine execution options. */
public record LintOptions(
        int parallelism,
        long perFileTimeoutMillis,
        FixPolicy fixPolicy,
        LintCache cache,
        int cacheMaxEntries
) {

    public static final int DEFAULT_PARALLELISM = 4;
    public static final long DEFAULT_TIMEOUT_MILLIS = 30_000L;

    public static LintOptions defaults() {
        return new LintOptions(DEFAULT_PARALLELISM, DEFAULT_TIMEOUT_MILLIS,
                FixPolicy.HIGHEST_RULE_ID, new DefaultLintCache(),
                DefaultLintCache.DEFAULT_MAX_ENTRIES);
    }

    public LintOptions withParallelism(int parallelism) {
        return new LintOptions(parallelism < 1 ? 1 : parallelism,
                perFileTimeoutMillis, fixPolicy, cache, cacheMaxEntries);
    }

    public LintOptions withTimeoutMillis(long timeoutMillis) {
        return new LintOptions(parallelism,
                timeoutMillis <= 0 ? Long.MAX_VALUE : timeoutMillis,
                fixPolicy, cache, cacheMaxEntries);
    }

    public LintOptions withFixPolicy(FixPolicy policy) {
        return new LintOptions(parallelism, perFileTimeoutMillis,
                policy == null ? FixPolicy.HIGHEST_RULE_ID : policy,
                cache, cacheMaxEntries);
    }

    public LintOptions withCache(LintCache cache) {
        return new LintOptions(parallelism, perFileTimeoutMillis, fixPolicy,
                cache == null ? new DefaultLintCache() : cache, cacheMaxEntries);
    }
}
