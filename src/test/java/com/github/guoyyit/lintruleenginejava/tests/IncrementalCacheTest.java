package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.cache.DefaultLintCache;
import com.github.guoyyit.lintruleenginejava.engine.CancellationToken;
import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.tests.fixtures.CountingPlugin;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 5: only changed files re-run; config/rule-set changes invalidate; cache is bounded. */
class IncrementalCacheTest {

    @BeforeEach
    void reset() {
        CountingPlugin.EXECUTIONS.set(0);
    }

    private LintEngine engine(TestProject p, int cacheSize) {
        PluginRegistry registry = p.registryOf(CountingPlugin.class);
        return new LintEngine(p.root, registry,
                LintOptions.defaults().withParallelism(2).withTimeoutMillis(10_000)
                        .withCache(new DefaultLintCache(cacheSize)));
    }

    @Test
    void unchangedFilesAreNotReChecked() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "clean\n");
            p.write("b.txt", "clean\n");
            LintEngine engine = engine(p, 100);

            LintReport first = engine.checkIncremental(new CancellationToken());
            assertTrue(first.diagnostics().isEmpty());
            assertEquals(2, CountingPlugin.EXECUTIONS.get());

            LintReport second = engine.checkIncremental(new CancellationToken());
            assertEquals(2, CountingPlugin.EXECUTIONS.get(),
                    "unchanged files must not be re-checked");
            assertEquals(first.diagnostics(), second.diagnostics());
        }
    }

    @Test
    void changedFileIsReCheckedAndDiagnosticAppears() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "clean\n");
            p.write("b.txt", "clean\n");
            LintEngine engine = engine(p, 100);
            engine.checkIncremental(new CancellationToken());
            assertEquals(2, CountingPlugin.EXECUTIONS.get());

            p.write("a.txt", "ZZZ now\n");
            LintReport report = engine.checkIncremental(new CancellationToken());
            assertEquals(3, CountingPlugin.EXECUTIONS.get(),
                    "only the changed file re-runs");
            assertEquals(1, report.diagnostics().size());
            assertEquals("a.txt", report.diagnostics().get(0).file().toString());
        }
    }

    @Test
    void configChangeInvalidatesCacheForFilesUnderThatConfig() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "ZZZ\n");
            p.write("sub/b.txt", "ZZZ\n");
            LintEngine engine = engine(p, 100);
            assertEquals(2, engine.check().diagnostics().size());

            // New parent config changes severity for every file: both re-check.
            p.write(".lintrc", "severity counter:marker=error\n");
            LintReport report = engine.checkIncremental(new CancellationToken());
            assertEquals(2, report.diagnostics().size());
            assertEquals(com.github.guoyyit.lintruleenginejava.model.Severity.ERROR,
                    report.diagnostics().get(0).severity());

            // A new child config only invalidates files in that subtree.
            CountingPlugin.EXECUTIONS.set(0);
            p.write("sub/.lintrc", "severity counter:marker=info\n");
            LintReport childReport = engine.checkIncremental(new CancellationToken());
            assertEquals(1, CountingPlugin.EXECUTIONS.get(),
                    "only sub/b.txt is under the changed child config");
            var byFile = new java.util.HashMap<String,
                    com.github.guoyyit.lintruleenginejava.model.Severity>();
            childReport.diagnostics().forEach(d ->
                    byFile.put(d.file().toString(), d.severity()));
            assertEquals(com.github.guoyyit.lintruleenginejava.model.Severity.INFO,
                    byFile.get("sub/b.txt"));
            assertEquals(com.github.guoyyit.lintruleenginejava.model.Severity.ERROR,
                    byFile.get("a.txt"));
        }
    }

    @Test
    void ruleSetChangeProducesDifferentFingerprintAndInvalidates() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "ZZZ\n");
            LintEngine v1 = engine(p, 100);
            assertEquals(1, v1.check().diagnostics().size());

            // A different registry (different fingerprint) but shared cache.
            DefaultLintCache shared = new DefaultLintCache(100);
            PluginRegistry r1 = p.registryOf(CountingPlugin.class);
            LintEngine e1 = new LintEngine(p.root, r1,
                    LintOptions.defaults().withCache(shared));
            e1.check();

            // Registry with the built-in plugin only has no counter rule.
            LintEngine e2 = new LintEngine(p.root, p.builtinRegistry(),
                    LintOptions.defaults().withCache(shared));
            LintReport report = e2.check();
            assertTrue(report.diagnostics().isEmpty(),
                    "different rule-set fingerprint must never reuse counter results");
        }
    }

    @Test
    void cacheIsBoundedByLruEviction() {
        DefaultLintCache cache = new DefaultLintCache(2);
        com.github.guoyyit.lintruleenginejava.cache.CacheKey k1 = key("a");
        com.github.guoyyit.lintruleenginejava.cache.CacheKey k2 = key("b");
        com.github.guoyyit.lintruleenginejava.cache.CacheKey k3 = key("c");
        cache.put(k1, new com.github.guoyyit.lintruleenginejava.cache.FileResult(java.util.List.of()));
        cache.put(k2, new com.github.guoyyit.lintruleenginejava.cache.FileResult(java.util.List.of()));
        assertTrue(cache.get(k1).isPresent()); // touch a -> b is LRU
        cache.put(k3, new com.github.guoyyit.lintruleenginejava.cache.FileResult(java.util.List.of()));
        assertEquals(2, cache.size());
        assertTrue(cache.get(k1).isPresent());
        assertTrue(cache.get(k3).isPresent());
        assertTrue(cache.get(k2).isEmpty(), "least-recently-used entry must be evicted");
    }

    private static com.github.guoyyit.lintruleenginejava.cache.CacheKey key(String h) {
        return new com.github.guoyyit.lintruleenginejava.cache.CacheKey(
                h, "cfg", "rules", "v");
    }
}
