package com.github.guoyyit.lintruleenginejava.lint.engine;

import com.github.guoyyit.lintruleenginejava.lint.config.ConfigResolver;
import com.github.guoyyit.lintruleenginejava.lint.config.LintConfig;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.Range;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.lint.suppression.SuppressionParser;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The lint engine: runs all enabled rules against files, applies suppression,
 * uses the incremental cache and executes checks concurrently with per-file
 * timeouts. Results are deterministic regardless of check order or concurrency:
 * diagnostics are sorted by (file, position, rule id, message) and file results
 * by path.
 */
public final class LintEngine {

    static final String RULE_ERROR_RULE_ID = "lint-engine:rule-error";
    private static final Range RANGE_AT_START = new Range(new Position(1, 1), new Position(1, 1));
    private static final Set<String> DIRECTORIES_TO_SKIP = Set.of(
            ".git", "target", "build", "node_modules", ".idea", ".gradle", "out");

    private final PluginRegistry registry;
    private final Path root;
    private final ConfigResolver configResolver;
    private final IncrementalCache cache;
    private final ExecutorService executor;
    private final long perFileTimeoutMillis;

    private LintEngine(Builder builder) {
        this.registry = Objects.requireNonNull(builder.registry, "registry");
        this.root = builder.root.toAbsolutePath().normalize();
        this.configResolver = new ConfigResolver(this.root, builder.globalConfig);
        this.cache = new IncrementalCache(builder.cacheSize);
        this.executor = Executors.newFixedThreadPool(builder.threads, r -> {
            Thread t = new Thread(r, "lint-worker");
            t.setDaemon(true);
            return t;
        });
        this.perFileTimeoutMillis = builder.perFileTimeoutMillis;
    }

    /** The root directory this engine resolves configs against. */
    public Path getRoot() {
        return root;
    }

    /** The plugin registry backing this engine. */
    public PluginRegistry getRegistry() {
        return registry;
    }

    /** The incremental cache (for inspection/testing). */
    public IncrementalCache getCache() {
        return cache;
    }

    /**
     * Checks the given files. Paths are resolved against the engine root and
     * processed in a deterministic order regardless of the input order.
     */
    public CheckResult check(List<Path> files) {
        List<Path> sorted = files.stream()
                .map(p -> p.isAbsolute() ? p.normalize() : root.resolve(p).normalize())
                .distinct()
                .sorted()
                .toList();

        List<Future<FileCheckResult>> futures = new ArrayList<>();
        for (Path file : sorted) {
            futures.add(executor.submit(() -> checkOneFile(file)));
        }

        List<FileCheckResult> fileResults = new ArrayList<>();
        int checked = 0;
        int cacheHits = 0;
        boolean cancelled = false;
        int cancelledAt = -1;
        try {
            for (int i = 0; i < futures.size(); i++) {
                Future<FileCheckResult> future = futures.get(i);
                Path file = sorted.get(i);
                FileCheckResult result;
                try {
                    if (perFileTimeoutMillis > 0) {
                        result = future.get(perFileTimeoutMillis, TimeUnit.MILLISECONDS);
                    } else {
                        result = future.get();
                    }
                } catch (TimeoutException e) {
                    future.cancel(true);
                    result = FileCheckResult.failure(file, FileCheckResult.Status.TIMED_OUT,
                            "Timed out after " + perFileTimeoutMillis + " ms");
                } catch (InterruptedException e) {
                    future.cancel(true);
                    cancelled = true;
                    cancelledAt = i;
                    Thread.currentThread().interrupt();
                    fileResults.add(FileCheckResult.failure(file,
                            FileCheckResult.Status.CANCELLED, "Check was cancelled"));
                    break;
                } catch (ExecutionException e) {
                    result = FileCheckResult.failure(file, FileCheckResult.Status.FAILED,
                            "Check failed: " + e.getCause().getMessage());
                }
                fileResults.add(result);
                if (result.fromCache()) {
                    cacheHits++;
                } else if (result.status() == FileCheckResult.Status.OK) {
                    checked++;
                }
            }
        } finally {
            if (cancelled) {
                // Cancel everything still in flight; never leave half-written state
                // (checks do not write anything). Completed results are kept.
                for (int j = cancelledAt + 1; j < futures.size(); j++) {
                    Future<FileCheckResult> future = futures.get(j);
                    Path file = sorted.get(j);
                    if (future.isDone() && !future.isCancelled()) {
                        try {
                            fileResults.add(future.get());
                        } catch (Exception e) {
                            fileResults.add(FileCheckResult.failure(file,
                                    FileCheckResult.Status.CANCELLED, "Check was cancelled"));
                        }
                    } else {
                        future.cancel(true);
                        fileResults.add(FileCheckResult.failure(file,
                                FileCheckResult.Status.CANCELLED, "Check was cancelled"));
                    }
                }
            }
        }

        fileResults.sort(Comparator.comparing(FileCheckResult::file));
        List<Diagnostic> diagnostics = fileResults.stream()
                .filter(r -> r.status() == FileCheckResult.Status.OK)
                .flatMap(r -> r.diagnostics().stream())
                .sorted()
                .toList();

        return new CheckResult(diagnostics, List.copyOf(fileResults),
                registry.getFailures(), checked, cacheHits);
    }

    /** Checks every regular file under the engine root (skipping build/VCS dirs). */
    public CheckResult checkDirectory() throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path fileName = dir.getFileName();
                if (fileName != null && DIRECTORIES_TO_SKIP.contains(fileName.toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return check(files);
    }

    /** Convenience: checks a single file. */
    public FileCheckResult checkOne(Path file) {
        return check(List.of(file)).fileResults().get(0);
    }

    private FileCheckResult checkOneFile(Path file) {
        if (Thread.currentThread().isInterrupted()) {
            return FileCheckResult.failure(file, FileCheckResult.Status.CANCELLED, "Check was cancelled");
        }
        SourceFile source;
        try {
            source = new SourceFile(file, Files.readString(file));
        } catch (IOException e) {
            return FileCheckResult.failure(file, FileCheckResult.Status.FAILED,
                    "Cannot read file: " + e.getMessage());
        }

        LintConfig config = configResolver.resolve(file);
        Object key = IncrementalCache.keyFor(source, config, registry);
        FileCheckResult cached = cache.get(key);
        if (cached != null) {
            return cached.asCacheHit();
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (LintRule rule : registry.getRules()) {
            if (Thread.currentThread().isInterrupted()) {
                return FileCheckResult.failure(file, FileCheckResult.Status.CANCELLED, "Check was cancelled");
            }
            if (!applies(rule, file)) {
                continue;
            }
            if (!config.isEnabled(rule.getId())) {
                continue;
            }
            try {
                for (Diagnostic d : rule.check(source)) {
                    diagnostics.add(normalize(d, rule, config, file));
                }
            } catch (Throwable t) {
                // A failing rule must not sink the whole file: other rules' results
                // are kept and the failure is locatable.
                diagnostics.add(Diagnostic.lint(RULE_ERROR_RULE_ID,
                        com.github.guoyyit.lintruleenginejava.lint.model.Severity.ERROR,
                        file, RANGE_AT_START,
                        "Rule '" + rule.getId() + "' failed: " + t.getMessage(), null));
            }
        }

        Set<String> knownRuleIds = new HashSet<>();
        registry.getRules().forEach(r -> knownRuleIds.add(r.getId()));
        List<Diagnostic> filtered = SuppressionParser.apply(source, diagnostics, knownRuleIds);
        FileCheckResult result = FileCheckResult.ok(file, filtered);
        cache.put(key, result);
        return result;
    }

    private static boolean applies(LintRule rule, Path file) {
        Set<String> extensions = rule.getApplicableExtensions();
        if (extensions.isEmpty()) {
            return true;
        }
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return extensions.contains(name.substring(dot + 1).toLowerCase());
    }

    private static Diagnostic normalize(Diagnostic d, LintRule rule, LintConfig config, Path file) {
        Range range = d.range() != null ? d.range() : RANGE_AT_START;
        var severity = config.effectiveSeverity(rule.getId(), rule.getDefaultSeverity());
        return Diagnostic.lint(d.ruleId() != null ? d.ruleId() : rule.getId(),
                severity, file, range, d.message(), d.fix());
    }

    /** Shuts down the worker pool. */
    public void close() {
        executor.shutdownNow();
    }

    public static Builder builder(PluginRegistry registry) {
        return new Builder(registry);
    }

    /** Builder for {@link LintEngine}. */
    public static final class Builder {
        private final PluginRegistry registry;
        private Path root = Path.of(".");
        private LintConfig globalConfig = LintConfig.EMPTY;
        private int cacheSize = 1000;
        private int threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        private long perFileTimeoutMillis = 60_000;

        private Builder(PluginRegistry registry) {
            this.registry = registry;
        }

        public Builder root(Path root) {
            this.root = root;
            return this;
        }

        public Builder globalConfig(LintConfig globalConfig) {
            this.globalConfig = globalConfig;
            return this;
        }

        public Builder cacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
            return this;
        }

        public Builder threads(int threads) {
            this.threads = threads;
            return this;
        }

        public Builder perFileTimeoutMillis(long perFileTimeoutMillis) {
            this.perFileTimeoutMillis = perFileTimeoutMillis;
            return this;
        }

        public LintEngine build() {
            return new LintEngine(this);
        }
    }
}
