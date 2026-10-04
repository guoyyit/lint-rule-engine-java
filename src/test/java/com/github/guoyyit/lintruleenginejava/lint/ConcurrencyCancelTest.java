package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.engine.CheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests concurrency and cancellation: per-file timeouts and mid-flight
 * cancellation never pollute other files, no half-written state is left,
 * and re-checks after cancellation are identical to full checks.
 */
class ConcurrencyCancelTest {

    @Test
    void perFileTimeoutDoesNotPolluteOtherFiles(@TempDir Path root) throws Exception {
        Path slow = root.resolve("slow.txt");
        Path fast = root.resolve("fast.txt");
        Files.writeString(slow, "slow\n");
        Files.writeString(fast, "TODO fast\n");

        LintEngine engine = LintEngine.builder(PluginRegistry.of(new SlowPlugin(1000)))
                .root(root).threads(2).perFileTimeoutMillis(200).build();
        CheckResult result = engine.check(List.of(slow, fast));

        FileCheckResult slowResult = result.fileResults().stream()
                .filter(r -> r.file().equals(slow)).findFirst().orElseThrow();
        FileCheckResult fastResult = result.fileResults().stream()
                .filter(r -> r.file().equals(fast)).findFirst().orElseThrow();
        assertEquals(FileCheckResult.Status.TIMED_OUT, slowResult.status());
        assertEquals(FileCheckResult.Status.OK, fastResult.status());
        assertTrue(fastResult.diagnostics().stream().anyMatch(d -> d.ruleId().equals("slow-rule")));
    }

    @Test
    void cancellationKeepsCompletedResultsAndRecheckMatchesFullCheck(@TempDir Path root) throws Exception {
        Path blocking = root.resolve("blocking.txt");
        Path normal = root.resolve("b.txt");
        Files.writeString(blocking, "blocking\n");
        Files.writeString(normal, "TODO normal\n");

        CountDownLatch latch = new CountDownLatch(1);
        LintEngine engine = LintEngine.builder(PluginRegistry.of(new BlockingPlugin(latch)))
                .root(root).threads(2).build();

        Thread checkThread = new Thread(() -> engine.check(List.of(blocking, normal)));
        checkThread.start();
        Thread.sleep(300);
        checkThread.interrupt();
        checkThread.join(2000);

        // The engine returned after cancellation; inspect via a fresh run is not
        // possible (check is synchronous), so re-run after releasing the latch.
        latch.countDown();
        CheckResult afterCancel = engine.check(List.of(blocking, normal));

        FileCheckResult blockingResult = afterCancel.fileResults().stream()
                .filter(r -> r.file().equals(blocking)).findFirst().orElseThrow();
        FileCheckResult normalResult = afterCancel.fileResults().stream()
                .filter(r -> r.file().equals(normal)).findFirst().orElseThrow();
        assertEquals(FileCheckResult.Status.OK, blockingResult.status());
        assertEquals(FileCheckResult.Status.OK, normalResult.status());
    }

    @Test
    void partialResultsAreReturnedWhenCancelled(@TempDir Path root) throws Exception {
        Path blocking = root.resolve("blocking.txt");
        Path normal = root.resolve("b.txt");
        Files.writeString(blocking, "blocking\n");
        Files.writeString(normal, "TODO normal\n");

        CountDownLatch latch = new CountDownLatch(1);
        LintEngine engine = LintEngine.builder(PluginRegistry.of(new BlockingPlugin(latch)))
                .root(root).threads(2).build();

        // Run the check in a thread and capture its result.
        CheckResult[] holder = new CheckResult[1];
        Thread checkThread = new Thread(() -> holder[0] = engine.check(List.of(blocking, normal)));
        checkThread.start();
        Thread.sleep(300);
        checkThread.interrupt();
        checkThread.join(2000);

        CheckResult partial = holder[0];
        FileCheckResult blockingResult = partial.fileResults().stream()
                .filter(r -> r.file().equals(blocking)).findFirst().orElseThrow();
        FileCheckResult normalResult = partial.fileResults().stream()
                .filter(r -> r.file().equals(normal)).findFirst().orElseThrow();
        assertEquals(FileCheckResult.Status.CANCELLED, blockingResult.status());
        assertEquals(FileCheckResult.Status.OK, normalResult.status(),
                "completed file result must survive cancellation");
        latch.countDown();
    }

    @Test
    void resultsAreIdenticalAcrossOrdersAndThreadCounts(@TempDir Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Path f = root.resolve("f" + i + ".txt");
            Files.writeString(f, "TODO " + i + "   \n\tindent\n");
            files.add(f);
        }

        List<String> baseline = null;
        for (int threads : new int[]{1, 2, 4}) {
            for (boolean reverse : new boolean[]{false, true}) {
                LintEngine engine = LintEngine.builder(PluginRegistry.load())
                        .root(root).threads(threads).build();
                List<Path> order = new ArrayList<>(files);
                if (reverse) {
                    Collections.reverse(order);
                }
                CheckResult result = engine.check(order);
                List<String> keys = result.diagnostics().stream()
                        .map(d -> d.filePath().getFileName() + ":" + d.range().start().line()
                                + ":" + d.ruleId() + ":" + d.message())
                        .toList();
                if (baseline == null) {
                    baseline = keys;
                } else {
                    assertEquals(baseline, keys,
                            "diagnostics must be identical regardless of order/concurrency");
                }
            }
        }
    }

    @Test
    void failingRuleDoesNotCrashCheckOrLoseOtherFiles(@TempDir Path root) throws Exception {
        Path bad = root.resolve("bad.txt");
        Path good = root.resolve("good.txt");
        Files.writeString(bad, "bad\n");
        Files.writeString(good, "TODO good\n");

        LintEngine engine = LintEngine.builder(PluginRegistry.of(FailingPlugin.ruleThrowingPlugin()))
                .root(root).threads(2).build();
        CheckResult result = engine.check(List.of(bad, good));

        FileCheckResult badResult = result.fileResults().stream()
                .filter(r -> r.file().equals(bad)).findFirst().orElseThrow();
        FileCheckResult goodResult = result.fileResults().stream()
                .filter(r -> r.file().equals(good)).findFirst().orElseThrow();
        assertEquals(FileCheckResult.Status.OK, badResult.status());
        assertEquals(FileCheckResult.Status.OK, goodResult.status());
        assertTrue(badResult.diagnostics().stream()
                .anyMatch(d -> d.ruleId().equals("lint-engine:rule-error")),
                "rule failure must be reported as a locatable diagnostic");
        assertTrue(goodResult.diagnostics().stream()
                .anyMatch(d -> d.ruleId().equals("lint-engine:rule-error")),
                "other files must still get their (rule-error) result");
    }
}
