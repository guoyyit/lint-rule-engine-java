package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.engine.CancellationToken;
import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end scenarios across plugins, config, suppression, fix and re-check. */
class EngineEndToEndTest {

    private LintEngine engine(TestProject p, int parallelism) {
        PluginRegistry registry = p.builtinRegistry();
        return new LintEngine(p.root, registry,
                LintOptions.defaults().withParallelism(parallelism).withTimeoutMillis(10_000));
    }

    @Test
    void autofixRemovesWhitespaceAndRecheckIsClean() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "foo   \nbar\t\n");
            LintReport fixed = engine(p, 1).checkAndFix(new CancellationToken());

            assertEquals("foo\nbar\n", p.read("a.txt"));
            assertTrue(fixed.diagnostics().isEmpty(),
                    "re-check after fixing must find no remaining issues: "
                            + fixed.diagnostics());
            assertEquals(1, fixed.fixResults().size());
            assertEquals(2, fixed.fixResults().get(0).appliedFixes().size());
        }
    }

    @Test
    void resultsStableAcrossManyConcurrentRuns() {
        try (TestProject p = TestProject.create()) {
            for (int i = 0; i < 20; i++) {
                p.write("dir" + (i % 5) + "/file" + i + ".txt",
                        "line" + i + "   \nTODO " + i + "\n");
            }
            List<List<Diagnostic>> all = new ArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int t = 0; t < 6; t++) {
                int parallelism = t + 1;
                Thread th = new Thread(() -> {
                    LintReport r = engine(p, parallelism).check();
                    synchronized (all) {
                        all.add(r.diagnostics());
                    }
                });
                threads.add(th);
                th.start();
            }
            for (Thread th : threads) {
                th.join(30_000);
            }
            assertEquals(6, all.size());
            for (List<Diagnostic> d : all) {
                assertEquals(all.get(0), d, "every concurrent run must yield identical ordered set");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
