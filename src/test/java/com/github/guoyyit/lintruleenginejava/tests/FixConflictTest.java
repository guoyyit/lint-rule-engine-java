package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.engine.CancellationToken;
import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.fix.FixResult;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.tests.fixtures.FixRules;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 4: overlapping fixes follow a deterministic policy; fixed files are re-checked. */
class FixConflictTest {

    private LintEngine engine(TestProject p, int parallelism) {
        PluginRegistry registry = p.registryOf(FixRules.LowPlugin.class,
                FixRules.HighPlugin.class, FixRules.OtherPlugin.class);
        return new LintEngine(p.root, registry,
                LintOptions.defaults().withParallelism(parallelism).withTimeoutMillis(10_000));
    }

    @Test
    void highestRuleIdWinsAndRejectedRuleIsReported() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "CONFLICT ELSE\n");

            LintReport report = engine(p, 1).checkAndFix(new CancellationToken());

            String after = p.read("a.txt");
            assertEquals("high done\n", after);
            assertEquals(1, report.fixResults().size());
            FixResult result = report.fixResults().get(0);
            assertEquals(1, result.conflicts().size());
            assertEquals(FixRules.HighPlugin.ID, result.conflicts().get(0).winnerRule());
            assertEquals(java.util.List.of(FixRules.LowPlugin.ID),
                    result.conflicts().get(0).rejectedRules());
            assertTrue(result.conflicts().get(0).reason().contains("HIGHEST_RULE_ID"));
        }
    }

    @Test
    void conflictOutcomeIsOrderIndependent() {
        String sequential;
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "CONFLICT\n");
            engine(p, 1).checkAndFix(new CancellationToken());
            sequential = p.read("a.txt");
        }
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "CONFLICT\n");
            engine(p, 8).checkAndFix(new CancellationToken());
            assertEquals(sequential, p.read("a.txt"));
        }
    }

    @Test
    void fixedFileIsRecheckedAndWinnerProblemDisappears() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "CONFLICT\n");
            LintReport report = engine(p, 1).checkAndFix(new CancellationToken());
            // After replacing the marker, neither conflicting rule finds anything.
            assertTrue(report.diagnostics().isEmpty(),
                    "post-fix diagnostics should be empty: " + report.diagnostics());
            assertEquals("high\n", p.read("a.txt"));
        }
    }

    @Test
    void runningFixTwiceIsStable() {
        try (TestProject p = TestProject.create()) {
            PluginRegistry registry = p.registryOf(FixRules.HighPlugin.class);
            var engine = new LintEngine(p.root, registry, LintOptions.defaults());
            p.write("a.txt", "CONFLICT\n");
            LintReport first = engine.checkAndFix(new CancellationToken());
            assertEquals("high\n", p.read("a.txt"));
            // Second run finds nothing to fix and does not rewrite the file.
            LintReport second = engine.checkAndFix(new CancellationToken());
            assertEquals("high\n", p.read("a.txt"));
            assertTrue(second.fixResults().stream().noneMatch(FixResult::changed));
            assertTrue(second.diagnostics().isEmpty());
            assertTrue(first.diagnostics().isEmpty());
        }
    }
}
