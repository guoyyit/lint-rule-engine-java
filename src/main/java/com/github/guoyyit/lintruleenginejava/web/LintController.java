package com.github.guoyyit.lintruleenginejava.web;

import com.github.guoyyit.lintruleenginejava.config.ConfigProblem;
import com.github.guoyyit.lintruleenginejava.engine.CheckFailure;
import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.fix.FixResult;
import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoadFailure;
import com.github.guoyyit.lintruleenginejava.plugin.PluginLoader;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.plugin.PluginSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP adapter over {@link LintEngine}. Each request builds a fresh
 * engine over {@code path} (default: the JVM working directory) loading
 * plugins through {@link java.util.ServiceLoader}.
 */
@RestController
public class LintController {

    @GetMapping("/lint")
    public Map<String, Object> lint(
            @RequestParam(name = "path", defaultValue = ".") String path,
            @RequestParam(name = "incremental", defaultValue = "false") boolean incremental,
            @RequestParam(name = "parallelism", defaultValue = "4") int parallelism,
            @RequestParam(name = "timeoutMillis", defaultValue = "30000") long timeoutMillis) {
        LintReportView report = buildEngine(path, parallelism, timeoutMillis)
                .map(e -> {
                    com.github.guoyyit.lintruleenginejava.engine.LintReport raw =
                            incremental ? e.checkIncremental(null) : e.check(null);
                    return new LintReportView(raw);
                })
                .orElseGet(() -> new LintReportView(
                        List.of(), List.of(), List.of(), List.of(), List.of(), 0, false));
        return report.toMap();
    }

    @PostMapping("/lint/fix")
    public Map<String, Object> fix(
            @RequestParam(name = "path", defaultValue = ".") String path,
            @RequestParam(name = "parallelism", defaultValue = "4") int parallelism,
            @RequestParam(name = "timeoutMillis", defaultValue = "30000") long timeoutMillis) {
        return buildEngine(path, parallelism, timeoutMillis)
                .map(e -> new LintReportView(e.checkAndFix(null)).toMap())
                .orElseGet(() -> new LintReportView(
                        List.of(), List.of(), List.of(), List.of(), List.of(), 0, false).toMap());
    }

    private static java.util.Optional<LintEngine> buildEngine(String path,
                                                              int parallelism, long timeout) {
        if (path == null || path.isBlank()) {
            return java.util.Optional.empty();
        }
        PluginRegistry registry = new PluginLoader().load(List.of(
                new PluginSource.ServiceLoader(
                        Thread.currentThread().getContextClassLoader(), Path.of(path))));
        LintOptions options = LintOptions.defaults()
                .withParallelism(parallelism)
                .withTimeoutMillis(timeout);
        return java.util.Optional.of(new LintEngine(Path.of(path).toAbsolutePath().normalize(),
                registry, options));
    }

    /** Stable JSON-friendly view of a {@code LintReport}. */
    public record LintReportView(
            List<DiagnosticView> diagnostics,
            List<FailureView> failures,
            List<PluginFailureView> pluginFailures,
            List<ConfigProblemView> configProblems,
            List<String> fixedFiles,
            int suppressedCount,
            boolean cancelled) {

        LintReportView(com.github.guoyyit.lintruleenginejava.engine.LintReport r) {
            this(r.diagnostics().stream().map(DiagnosticView::new).toList(),
                    r.failures().stream().map(FailureView::new).toList(),
                    r.pluginFailures().stream().map(PluginFailureView::new).toList(),
                    r.configProblems().stream().map(ConfigProblemView::new).toList(),
                    r.fixResults().stream().filter(FixResult::changed)
                            .map(f -> f.file().toString()).sorted().distinct().toList(),
                    r.suppressedCount(), r.cancelled());
        }

        Map<String, Object> toMap() {
            return Map.of(
                    "diagnostics", diagnostics,
                    "failures", failures,
                    "pluginFailures", pluginFailures,
                    "configProblems", configProblems,
                    "fixedFiles", fixedFiles,
                    "suppressedCount", suppressedCount,
                    "cancelled", cancelled,
                    "clean", diagnostics.isEmpty() && failures.isEmpty());
        }
    }

    public record DiagnosticView(String rule, String file, int line, int column,
                                 String severity, String message, boolean autoFixable) {
        DiagnosticView(Diagnostic d) {
            this(d.ruleId().toString(), d.file().toString(), d.line(), d.column(),
                    d.severity().name(), d.message(), d.fix() != null);
        }
    }

    public record FailureView(String file, String reason, String cause) {
        FailureView(CheckFailure f) {
            this(f.file().toString(), f.reason(), f.causeClass());
        }
    }

    public record PluginFailureView(String plugin, String source, String reason, String cause) {
        PluginFailureView(PluginLoadFailure f) {
            this(f.descriptor() == null ? "<unknown>" : f.descriptor().name(),
                    f.location(), f.reason(), f.causeClass());
        }
    }

    public record ConfigProblemView(String file, int line, String message) {
        ConfigProblemView(ConfigProblem p) {
            this(p.configFile().toString(), p.line(), p.message());
        }
    }
}
