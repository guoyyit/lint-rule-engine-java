package com.github.guoyyit.lintruleenginejava.lint.engine;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginLoadFailure;

import java.util.List;

/**
 * The outcome of a full check run.
 *
 * @param diagnostics      all diagnostics, stably sorted
 * @param fileResults      per-file outcomes (including failed/timed-out files)
 * @param pluginFailures   plugins that could not be loaded
 * @param checkedFiles     number of files actually checked (cache misses)
 * @param cacheHits        number of files served from the incremental cache
 */
public record CheckResult(List<Diagnostic> diagnostics,
                          List<FileCheckResult> fileResults,
                          List<PluginLoadFailure> pluginFailures,
                          int checkedFiles,
                          int cacheHits) {
}
