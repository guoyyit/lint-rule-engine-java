package com.github.guoyyit.lintruleenginejava.engine;

import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;

import java.nio.file.Path;

/**
 * Orchestrates the full analysis: configuration resolution, rule filtering,
 * concurrent per-file checking with timeout/cancellation isolation, inline
 * suppression, deterministic ordering, bounded incremental caching and
 * conflict-safe auto-fixing.
 */
public final class LintEngine {

    public LintEngine(Path root, PluginRegistry registry) {
        // skeleton
    }

    public LintEngine(Path root, PluginRegistry registry, LintOptions options) {
        // skeleton
    }

    /** Full check of the analysis root. */
    public LintReport check() {
        // skeleton
        return null;
    }

    /** Full check with cooperative cancellation. */
    public LintReport check(CancellationToken token) {
        // skeleton
        return null;
    }

    /** Incremental check: only files whose inputs changed are re-checked. */
    public LintReport checkIncremental(CancellationToken token) {
        // skeleton
        return null;
    }

    /** Apply automatic fixes, then re-check fixed files to confirm. */
    public LintReport checkAndFix(CancellationToken token) {
        // skeleton
        return null;
    }
}
