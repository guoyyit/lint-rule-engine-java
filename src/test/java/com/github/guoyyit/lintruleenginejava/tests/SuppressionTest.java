package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.suppress.SuppressionParser;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 3: inline suppression with well-defined scope and locatable marker errors. */
class SuppressionTest {

    private static final RuleId TRAILING = RuleId.of("builtin", "trailing-whitespace");
    private static final RuleId TODO = RuleId.of("builtin", "no-todo");

    private LintReport run(TestProject p) {
        PluginRegistry registry = p.builtinRegistry();
        LintEngine engine = new LintEngine(p.root, registry,
                LintOptions.defaults().withParallelism(4));
        return engine.check();
    }

    @Test
    void markerOnSameLineSuppressesThatLineOnly() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "bad line   // lint:suppress builtin:trailing-whitespace\n"
                    + "also bad   \n");
            LintReport report = run(p);
            assertEquals(1, report.suppressedCount());
            assertEquals(1, report.diagnostics().size());
            assertEquals(2, report.diagnostics().get(0).line());
        }
    }

    @Test
    void markerOnOwnLineSuppressesNextLine() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "// lint:suppress builtin:trailing-whitespace\n"
                    + "bad   \n"
                    + "bad2   \n");
            LintReport report = run(p);
            assertEquals(1, report.diagnostics().size());
            assertEquals(3, report.diagnostics().get(0).line());
        }
    }

    @Test
    void suppressAllCoversEveryRule() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "TODO   // lint:suppress-all\n"
                    + "TODO x\n");
            LintReport report = run(p);
            // Line 1 (trailing whitespace + todo) suppressed; line 2 TODO remains.
            assertEquals(1, report.diagnostics().size());
            assertEquals(TODO, report.diagnostics().get(0).ruleId());
        }
    }

    @Test
    void disableRegionHasExclusiveEndpoints() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "a   \n"
                    + "// lint:disable builtin:trailing-whitespace\n"
                    + "b   \n"
                    + "// lint:enable builtin:trailing-whitespace\n"
                    + "c   \n");
            LintReport report = run(p);
            // Line 1 before the region and line 5 after it are reported;
            // line 3 inside is not; marker lines 2 and 4 have no whitespace.
            assertEquals(List.of(1, 5),
                    report.diagnostics().stream().map(Diagnostic::line).toList());
        }
    }

    @Test
    void disableRegionWithoutEnableReachesEndOfFile() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "a   \n"
                    + "// lint:disable builtin:trailing-whitespace\n"
                    + "b   \n"
                    + "c   \n");
            LintReport report = run(p);
            assertEquals(List.of(1),
                    report.diagnostics().stream().map(Diagnostic::line).toList());
        }
    }

    @Test
    void unknownRuleInMarkerProducesLocateableDiagnostic() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "// lint:suppress builtin:nope\nx\n");
            LintReport report = run(p);
            assertEquals(1, report.diagnostics().size());
            Diagnostic d = report.diagnostics().get(0);
            assertEquals(SuppressionParser.MARKER_RULE, d.ruleId());
            assertEquals(1, d.line());
            assertTrue(d.message().contains("unknown rule"), d.message());
            assertTrue(d.message().contains("builtin:nope"), d.message());
        }
    }

    @Test
    void malformedMarkerAndBadArgumentsAreReported() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "// lint:suppress not-a-rule-ref\n"
                    + "// lint:suppress-all builtin:trailing-whitespace\n");
            var set = new SuppressionParser().parse(p.read("a.txt"),
                    Path.of("a.txt"), Set.of(TRAILING, TODO));
            assertEquals(2, set.markerProblems().size());
            assertTrue(set.markerProblems().get(0).message().contains("malformed rule reference"));
            assertTrue(set.markerProblems().get(1).message().contains("takes no arguments"));
        }
    }

    @Test
    void suppressedDiagnosticNeverAppearsInFinalReport() {
        try (TestProject p = TestProject.create()) {
            p.write("a.txt", "bad   // lint:suppress builtin:trailing-whitespace\n");
            LintReport report = run(p);
            assertTrue(report.diagnostics().isEmpty());
            assertTrue(report.clean());
            assertEquals(1, report.suppressedCount());
        }
    }
}
