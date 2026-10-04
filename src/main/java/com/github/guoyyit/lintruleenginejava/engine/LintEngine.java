package com.github.guoyyit.lintruleenginejava.engine;

import com.github.guoyyit.lintruleenginejava.cache.CacheKey;
import com.github.guoyyit.lintruleenginejava.cache.FileResult;
import com.github.guoyyit.lintruleenginejava.config.ConfigProblem;
import com.github.guoyyit.lintruleenginejava.config.ConfigResolver;
import com.github.guoyyit.lintruleenginejava.config.LintConfig;
import com.github.guoyyit.lintruleenginejava.fix.FixApplicator;
import com.github.guoyyit.lintruleenginejava.fix.FixResult;
import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;
import com.github.guoyyit.lintruleenginejava.suppress.SuppressionParser;
import com.github.guoyyit.lintruleenginejava.suppress.SuppressionSet;
import com.github.guoyyit.lintruleenginejava.util.Globs;
import com.github.guoyyit.lintruleenginejava.util.Hashes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Orchestrates the full analysis: configuration resolution, rule filtering,
 * concurrent per-file checking with timeout/cancellation isolation, inline
 * suppression, deterministic ordering, bounded incremental caching and
 * conflict-safe auto-fixing.
 *
 * <p>Instances are reusable across runs and thread-safe for {@link #check()}
 * style invocations; incremental state is updated after every run.
 */
public final class LintEngine {

    private final Path root;
    private final PluginRegistry registry;
    private final LintOptions options;
    private final ConfigResolver configResolver;
    private final SuppressionParser suppressionParser = new SuppressionParser();
    private final Set<RuleId> knownRuleIds;
    private final Map<Path, String> lastHashes = new ConcurrentHashMap<>();

    public LintEngine(Path root, PluginRegistry registry) {
        this(root, registry, LintOptions.defaults());
    }

    public LintEngine(Path root, PluginRegistry registry, LintOptions options) {
        if (root == null || registry == null) {
            throw new IllegalArgumentException("root and registry must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
        this.registry = registry;
        this.options = options == null ? LintOptions.defaults() : options;
        this.configResolver = new ConfigResolver(this.root);
        Set<RuleId> ids = new HashSet<>();
        for (Rule r : this.registry.rules()) {
            ids.add(r.id());
        }
        this.knownRuleIds = Set.copyOf(ids);
    }

    /** Full check of the analysis root. */
    public LintReport check() {
        return run(false, false, CancellationToken.none());
    }

    /** Full check with cooperative cancellation. */
    public LintReport check(CancellationToken token) {
        return run(false, false, token == null ? CancellationToken.none() : token);
    }

    /** Incremental check: only files whose inputs changed are re-checked. */
    public LintReport checkIncremental(CancellationToken token) {
        return run(true, false, token == null ? CancellationToken.none() : token);
    }

    /** Apply automatic fixes, then re-check fixed files to confirm. */
    public LintReport checkAndFix(CancellationToken token) {
        return run(false, true, token == null ? CancellationToken.none() : token);
    }

    // ------------------------------------------------------------------
    // Main run
    // ------------------------------------------------------------------

    private LintReport run(boolean incremental, boolean withFixes, CancellationToken globalToken) {
        List<Path> files = discoverFiles();

        // Per-file work runs on a fresh pool per invocation, so a cancelled or
        // timed-out run can never pollute another run's workers.
        int threads = Math.max(1, Math.min(options.parallelism(), Math.max(1, files.size())));
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "lint-worker");
            t.setDaemon(true);
            return t;
        });
        List<FileOutcome> outcomes = new ArrayList<>();
        boolean cancelled;
        try {
            List<Future<FileOutcome>> futures = new ArrayList<>();
            List<CancellationToken> fileTokens = new ArrayList<>();
            for (Path file : files) {
                CancellationToken fileToken = new CancellationToken();
                fileTokens.add(fileToken);
                futures.add(pool.submit((Callable<FileOutcome>) () ->
                        checkFile(file, incremental, globalToken, fileToken)));
            }
            for (int i = 0; i < futures.size(); i++) {
                FileOutcome outcome;
                try {
                    outcome = futures.get(i).get(options.perFileTimeoutMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    futures.get(i).cancel(true);
                    fileTokens.get(i).cancel();
                    outcome = FileOutcome.failed(files.get(i),
                            "per-file check timed out after "
                                    + options.perFileTimeoutMillis() + " ms",
                            TimeoutException.class.getName());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    outcome = FileOutcome.failed(files.get(i),
                            "unexpected check error: " + cause.getMessage(),
                            cause.getClass().getName());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    globalToken.cancel();
                    for (Future<FileOutcome> f : futures) {
                        f.cancel(true);
                    }
                    outcome = FileOutcome.failed(files.get(i), "engine interrupted",
                            InterruptedException.class.getName());
                }
                outcomes.add(outcome);
            }
        } finally {
            pool.shutdownNow();
        }
        cancelled = globalToken.isCancelled();

        // Deterministic aggregation, independent of completion order.
        outcomes.sort(Comparator.comparing(o -> o.file));
        List<Diagnostic> allDiagnostics = new ArrayList<>();
        List<CheckFailure> failures = new ArrayList<>();
        Set<ConfigProblem> uniqueProblems = new HashSet<>();
        List<ConfigProblem> configProblems = new ArrayList<>();
        AtomicInteger suppressed = new AtomicInteger();
        for (FileOutcome o : outcomes) {
            if (o.failure != null) {
                failures.add(o.failure);
                continue;
            }
            allDiagnostics.addAll(o.diagnostics);
            suppressed.addAndGet(o.suppressedCount);
            for (ConfigProblem p : configResolver.problemsFor(o.file)) {
                if (uniqueProblems.add(p)) {
                    configProblems.add(p);
                }
            }
        }
        allDiagnostics.sort(Diagnostic::compareTo);
        failures.sort(Comparator.comparing(CheckFailure::file)
                .thenComparing(CheckFailure::reason));
        configProblems.sort(Comparator.comparing(ConfigProblem::configFile)
                .thenComparingInt(ConfigProblem::line));

        List<FixResult> fixResults = List.of();
        if (withFixes && !cancelled) {
            FixPhase phase = applyFixes(outcomes, globalToken);
            fixResults = phase.fixResults;
            failures.addAll(phase.fixFailures);
            // Replace diagnostics of every fixed file with a fresh re-check,
            // proving the reported problems are gone / what remains.
            allDiagnostics = recheckAfterFix(outcomes, phase, allDiagnostics,
                    failures, globalToken);
            allDiagnostics.sort(Diagnostic::compareTo);
        }

        // Persist incremental state and cache entries only for fully known files.
        if (!cancelled) {
            Map<Path, String> newHashes = new ConcurrentHashMap<>();
            for (FileOutcome o : outcomes) {
                if (o.hash != null) {
                    newHashes.put(o.file, o.hash);
                    if (o.failure == null) {
                        options.cache().put(cacheKey(o.file, o.hash),
                                new FileResult(List.copyOf(o.diagnostics)));
                    }
                }
            }
            lastHashes.clear();
            lastHashes.putAll(newHashes);
        }

        return new LintReport(List.copyOf(allDiagnostics), List.copyOf(failures),
                List.copyOf(registry.failures()), List.copyOf(configProblems),
                fixResults, suppressed.get(), cancelled);
    }

    // ------------------------------------------------------------------
    // Per-file analysis
    // ------------------------------------------------------------------

    private FileOutcome checkFile(Path relativeFile, boolean incremental,
                                  CancellationToken globalToken,
                                  CancellationToken fileToken) {
        Path abs = root.resolve(relativeFile);
        String content;
        String hash;
        try {
            content = Files.readString(abs, StandardCharsets.UTF_8);
            hash = Hashes.sha256(content);
        } catch (IOException e) {
            return FileOutcome.failed(relativeFile,
                    "unreadable input file: " + e.getMessage(), e.getClass().getName());
        }
        String configFp = configResolver.fingerprint(relativeFile);
        CacheKey key = new CacheKey(hash, configFp, registry.rulesFingerprint(),
                CacheKey.ENGINE_VERSION);

        if (incremental) {
            String previous = lastHashes.get(relativeFile);
            if (hash.equals(previous)) {
                Optional<FileResult> cached = options.cache().get(key);
                if (cached.isPresent()) {
                    return FileOutcome.cached(relativeFile, hash, content,
                            new ArrayList<>(cached.get().diagnostics()), 0);
                }
            }
        } else {
            Optional<FileResult> cached = options.cache().get(key);
            if (cached.isPresent()) {
                return FileOutcome.cached(relativeFile, hash, content,
                        new ArrayList<>(cached.get().diagnostics()), 0);
            }
        }

        LintConfig config = configResolver.resolve(relativeFile);
        if (!config.globs().isEmpty() && !Globs.matchesAny(config.globs(), relativeFile.toString())) {
            return FileOutcome.ok(relativeFile, hash, content, List.of(), 0);
        }

        SuppressionSet suppressions = suppressionParser.parse(content, relativeFile, knownRuleIds);
        CollectingContext ctx = new CollectingContext(relativeFile, abs, content,
                config, suppressions);

        for (Rule rule : registry.rules()) {
            if (globalToken.isCancelled() || fileToken.isCancelled()
                    || Thread.currentThread().isInterrupted()) {
                return FileOutcome.failed(relativeFile, "check cancelled",
                        "cancellation");
            }
            if (config.isDisabled(rule.id())) {
                continue;
            }
            if (!Globs.matches(rule.appliesTo(), relativeFile.toString())) {
                continue;
            }
            try {
                rule.check(ctx);
            } catch (RuntimeException | Error t) {
                // One rogue rule must not abort the file's other rules.
                return FileOutcome.failed(relativeFile,
                        "rule " + rule.id() + " threw: " + t.getClass().getSimpleName()
                                + (t.getMessage() != null ? ": " + t.getMessage() : ""),
                        t.getClass().getName());
            }
        }

        ctx.diagnostics.addAll(suppressions.markerProblems());
        ctx.diagnostics.sort(Diagnostic::compareTo);
        return FileOutcome.ok(relativeFile, hash, content,
                List.copyOf(ctx.diagnostics), ctx.suppressedCount.get());
    }

    private CacheKey cacheKey(Path relativeFile, String hash) {
        return new CacheKey(hash, configResolver.fingerprint(relativeFile),
                registry.rulesFingerprint(), CacheKey.ENGINE_VERSION);
    }

    // ------------------------------------------------------------------
    // Fix phase
    // ------------------------------------------------------------------

    private FixPhase applyFixes(List<FileOutcome> outcomes, CancellationToken token) {
        List<FixResult> results = new ArrayList<>();
        List<CheckFailure> failures = new ArrayList<>();
        FixApplicator applicator = new FixApplicator(options.fixPolicy());
        for (FileOutcome o : outcomes) {
            if (token.isCancelled()) {
                break;
            }
            if (o.failure != null || o.diagnostics.isEmpty()) {
                continue;
            }
            boolean anyFix = o.diagnostics.stream().anyMatch(d -> d.fix() != null);
            if (!anyFix) {
                continue;
            }
            FixResult result = applicator.apply(o.file, o.content, o.diagnostics);
            if (!result.changed()) {
                if (!result.conflicts().isEmpty()) {
                    results.add(result);
                }
                continue;
            }
            Path abs = root.resolve(o.file);
            // Guard against clobbering user edits made after our read.
            String currentHash;
            try {
                currentHash = Hashes.sha256(Files.readString(abs, StandardCharsets.UTF_8));
            } catch (IOException e) {
                failures.add(new CheckFailure(o.file,
                        "fix skipped, file unreadable before write: " + e.getMessage(),
                        e.getClass().getName()));
                continue;
            }
            if (!currentHash.equals(o.hash)) {
                failures.add(new CheckFailure(o.file,
                        "fix skipped: file was modified concurrently after analysis",
                        "concurrent-modification"));
                continue;
            }
            try {
                writeAtomically(abs, result.newContent());
            } catch (IOException e) {
                failures.add(new CheckFailure(o.file,
                        "fix not written: " + e.getMessage(), e.getClass().getName()));
                continue;
            }
            o.content = result.newContent();
            o.hash = Hashes.sha256(result.newContent());
            results.add(result);
        }
        results.sort(Comparator.comparing(FixResult::file));
        return new FixPhase(results, failures);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".lint-fix-"
                + Long.toHexString(System.nanoTime()) + ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Non-atomic fallback only after the complete new content is
                // durable in the temp file; the target itself is never opened
                // in write mode, so no half-written target can be observed.
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private List<Diagnostic> recheckAfterFix(List<FileOutcome> outcomes, FixPhase phase,
                                             List<Diagnostic> previous,
                                             List<CheckFailure> failures,
                                             CancellationToken token) {
        Set<Path> fixedFiles = new HashSet<>();
        for (FixResult r : phase.fixResults) {
            fixedFiles.add(r.file());
        }
        List<Diagnostic> kept = new ArrayList<>();
        for (Diagnostic d : previous) {
            if (!fixedFiles.contains(d.file())) {
                kept.add(d);
            }
        }
        for (FileOutcome o : outcomes) {
            if (!fixedFiles.contains(o.file) || token.isCancelled()) {
                continue;
            }
            SuppressionSet suppressions = suppressionParser.parse(o.content, o.file, knownRuleIds);
            LintConfig config = configResolver.resolve(o.file);
            CollectingContext ctx = new CollectingContext(o.file, root.resolve(o.file),
                    o.content, config, suppressions);
            for (Rule rule : registry.rules()) {
                if (config.isDisabled(rule.id())
                        || !Globs.matches(rule.appliesTo(), o.file.toString())) {
                    continue;
                }
                try {
                    rule.check(ctx);
                } catch (RuntimeException | Error t) {
                    failures.add(new CheckFailure(o.file,
                            "post-fix recheck rule " + rule.id() + " threw: "
                                    + t.getClass().getSimpleName(),
                            t.getClass().getName()));
                }
            }
            ctx.diagnostics.addAll(suppressions.markerProblems());
            kept.addAll(ctx.diagnostics);
            o.diagnostics = List.copyOf(ctx.diagnostics);
            o.suppressedCount = ctx.suppressedCount.get();
        }
        return kept;
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    private List<Path> discoverFiles() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(abs -> {
                Path rel = root.relativize(abs);
                if (isHidden(rel) || rel.getFileName().toString().equals(ConfigResolver.CONFIG_FILE_NAME)) {
                    return;
                }
                files.add(rel);
            });
        } catch (IOException e) {
            return List.of();
        }
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static boolean isHidden(Path relative) {
        for (Path part : relative) {
            String name = part.getFileName().toString();
            if (!name.equals(".") && !name.equals("..") && name.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Internal types
    // ------------------------------------------------------------------

    private static final class FileOutcome {
        final Path file;
        String hash;
        String content;
        List<Diagnostic> diagnostics;
        int suppressedCount;
        final CheckFailure failure;

        private FileOutcome(Path file, String hash, String content,
                            List<Diagnostic> diagnostics, int suppressedCount,
                            CheckFailure failure) {
            this.file = file;
            this.hash = hash;
            this.content = content;
            this.diagnostics = diagnostics;
            this.suppressedCount = suppressedCount;
            this.failure = failure;
        }

        static FileOutcome ok(Path file, String hash, String content,
                              List<Diagnostic> diagnostics, int suppressed) {
            return new FileOutcome(file, hash, content, diagnostics, suppressed, null);
        }

        static FileOutcome cached(Path file, String hash, String content,
                                  List<Diagnostic> diagnostics, int suppressed) {
            return new FileOutcome(file, hash, content, diagnostics, suppressed, null);
        }

        static FileOutcome failed(Path file, String reason, String cause) {
            return new FileOutcome(file, null, null, List.of(), 0,
                    new CheckFailure(file, reason, cause));
        }
    }

    private record FixPhase(List<FixResult> fixResults, List<CheckFailure> fixFailures) {
    }

    private final class CollectingContext implements RuleContext {
        private final Path relativeFile;
        private final Path absoluteFile;
        private final String content;
        private final LintConfig config;
        private final SuppressionSet suppressions;
        private final List<Diagnostic> diagnostics = new ArrayList<>();
        private final AtomicInteger suppressedCount = new AtomicInteger();

        CollectingContext(Path relativeFile, Path absoluteFile, String content,
                          LintConfig config, SuppressionSet suppressions) {
            this.relativeFile = relativeFile;
            this.absoluteFile = absoluteFile;
            this.content = content;
            this.config = config;
            this.suppressions = suppressions;
        }

        @Override
        public Path relativePath() {
            return relativeFile;
        }

        @Override
        public Path absolutePath() {
            return absoluteFile;
        }

        @Override
        public String content() {
            return content;
        }

        @Override
        public boolean isSuppressed(RuleId ruleId, int line) {
            return suppressions.isSuppressed(ruleId, line);
        }

        @Override
        public void report(RuleId ruleId, int line, int column, Severity severity,
                           String message, Fix fix) {
            if (ruleId == null) {
                throw new IllegalArgumentException("ruleId must not be null");
            }
            if (suppressions.isSuppressed(ruleId, line)) {
                suppressedCount.incrementAndGet();
                return;
            }
            Severity effective = config.effectiveSeverity(ruleId, severity);
            diagnostics.add(new Diagnostic(ruleId, relativeFile, line, column,
                    effective, message, fix));
        }
    }
}
