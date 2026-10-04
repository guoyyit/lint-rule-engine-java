package com.github.guoyyit.lintruleenginejava.engine;

import com.github.guoyyit.lintruleenginejava.config.ConfigProblem;
import com.github.guoyyit.lintruleenginejava.fix.FixResult;
import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoadFailure;

import java.util.List;

/**
 * The complete, deterministically ordered result of a lint run.
 */
public record LintReport(
        List<Diagnostic> diagnostics,
        List<CheckFailure> failures,
        List<PluginLoadFailure> pluginFailures,
        List<ConfigProblem> configProblems,
        List<FixResult> fixResults,
        int suppressedCount,
        boolean cancelled
) {

    public boolean clean() {
        // skeleton
        return false;
    }
}
