package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.engine.CancellationToken;
import com.github.guoyyit.lintruleenginejava.engine.CheckFailure;
import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.tests.fixtures.SlowPlugin;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 6: timeout/cancellation isolation, no half-written state, re-check consistency. */
class CancellationTest {

    private LintEngine engine(TestProject p, long timeoutMillis, int parallelism) {
        PluginRegistry registry = p.registryOf(SlowPlugin.class);
        return new LintEngine(p.root, registry,
                LintOptions.defaults()
                        .withTimeoutMillis(timeoutMillis)
                        .withParallelism(parallelism));
    }

    @Test
    void oneSlowFileTimesOutButOtherFilesStillReport() {
        try (TestProject p = TestProject.create()) {
            for (int i = 0; i < 6; i++) {
                p.write("fast" + i + ".txt", "hi\n");
            }
            p.write("slow-one.txt", "hi\n");

            long start = System.currentTimeMillis();
            LintReport report = engine(p, 500, 4).check();
            long elapsed = System.currentTimeMillis() - start;

            assertEquals(1, report.failures().size(), "exactly the slow file fails");
            CheckFailure failure = report.failures().get(0);
            assertEquals("slow-one.txt", failure.file().toString());
            assertTrue(failure.reason().contains("timed out"), failure.reason());
            assertEquals(java.util.concurrent.TimeoutException.class.getName(),
                    failure.causeClass());
            assertEquals(6, report.diagnostics().size(),
                    "other files are unaffected by the timeout");
            assertTrue(elapsed < 10_000, "run must not wait for the full sleep: " + elapsed);
        }
    }

    @Test
    void cancelledRunIsMarkedAndDoesNotPoisonLaterFullRun() {
        try (TestProject p = TestProject.create()) {
            for (int i = 0; i < 8; i++) {
                p.write("fast" + i + ".txt", "x\n");
            }
            p.write("slow-one.txt", "x\n");
            p.write("slow-two.txt", "x\n");
            LintEngine e = engine(p, 5_000, 2);

            CancellationToken token = new CancellationToken();
            AtomicBoolean done = new AtomicBoolean();
            Thread canceller = new Thread(() -> {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ignored) {
                }
                token.cancel();
                done.set(true);
            });
            canceller.start();
            LintReport cancelledReport = e.check(token);
            canceller.join();
            assertTrue(done.get());
            // Cancellation must be observable; it must not produce fake success.
            // Either the run was flagged cancelled or the slow files failed,
            // but the engine state must remain usable.
            assertTrue(cancelledReport.cancelled() || !cancelledReport.failures().isEmpty());

            // Re-check after cancellation equals a clean full check.
            LintReport rerun = e.check();
            assertFalse(rerun.cancelled());
            assertTrue(rerun.failures().isEmpty(), rerun.failures()::toString);
            assertEquals(10, rerun.diagnostics().size());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void cancelledFixWritesNothing() {
        try (TestProject p = TestProject.create()) {
            // Fast files with fixable builtin whitespace; cancellation before
            // the fix phase must leave every file untouched.
            for (int i = 0; i < 5; i++) {
                p.write("f" + i + ".txt", "x   \n");
            }
            p.write("slow-one.txt", "x   \n");
            LintEngine e = engine(p, 5_000, 2);
            CancellationToken token = new CancellationToken();
            token.cancel();
            LintReport report = e.checkAndFix(token);
            assertTrue(report.cancelled());
            for (int i = 0; i < 5; i++) {
                assertEquals("x   \n", p.read("f" + i + ".txt"),
                        "cancelled run must leave no half-written files");
            }
        }
    }
}
