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

    public static LintOptions defaults() {
        // skeleton
        return null;
    }

    public LintOptions withParallelism(int parallelism) {
        // skeleton
        return null;
    }

    public LintOptions withTimeoutMillis(long timeoutMillis) {
        // skeleton
        return null;
    }
}
