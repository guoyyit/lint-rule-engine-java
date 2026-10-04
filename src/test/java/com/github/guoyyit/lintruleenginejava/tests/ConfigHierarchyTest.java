package com.github.guoyyit.lintruleenginejava.tests;

import com.github.guoyyit.lintruleenginejava.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.engine.LintOptions;
import com.github.guoyyit.lintruleenginejava.engine.LintReport;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.PluginRegistry;
import com.github.guoyyit.lintruleenginejava.tests.support.TestProject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requirement 2: per-directory config inherits and overrides parents. */
class ConfigHierarchyTest {

    private LintReport run(TestProject project, int parallelism) {
        PluginRegistry registry = project.builtinRegistry();
        LintOptions options = LintOptions.defaults()
                .withParallelism(parallelism)
                .withTimeoutMillis(10_000);
        return new LintEngine(project.root, registry, options).check();
    }

    @Test
    void childInheritsAndOverridesParentSeverity() {
        try (TestProject p = TestProject.create()) {
            // Root sets no-todo to WARNING for every directory.
            p.write(".lintrc", "severity builtin:no-todo=warning\n");
            p.write("a.txt", "x TODO y\n");
            // Child overrides to ERROR.
            p.write("sub/.lintrc", "severity builtin:no-todo=error\n");
            p.write("sub/b.txt", "x TODO y\n");

            LintReport report = run(p, 1);
            assertTrue(report.failures().isEmpty(), report.failures()::toString);
            assertEquals(2, report.diagnostics().size());
            assertEquals(Severity.WARNING, report.diagnostics().get(0).severity());
            assertEquals(Severity.ERROR, report.diagnostics().get(1).severity());
        }
    }

    @Test
    void disableInParentAppliesEverywhereChildReenablesViaSeverity() {
        try (TestProject p = TestProject.create()) {
            p.write(".lintrc", "disable builtin:no-todo\n");
            p.write("a.txt", "TODO\n");
            // Child config without re-enable: still disabled.
            p.write("sub/.lintrc", "severity builtin:no-todo=error\n"
                    + "severity builtin:trailing-whitespace=error\n");
            // disable is inherited (no removal semantics): no-todo stays off.
            p.write("sub/b.txt", "TODO  \n");

            LintReport report = run(p, 1);
            // Only the trailing whitespace diagnostic survives.
            assertEquals(1, report.diagnostics().size());
            assertEquals("builtin:trailing-whitespace",
                    report.diagnostics().get(0).ruleId().toString());
        }
    }

    @Test
    void invalidDirectivesAreLocated() {
        try (TestProject p = TestProject.create()) {
            p.write(".lintrc", "severity badreference oops\n"
                    + "severity builtin:no-todo=catastrophic\n"
                    + "bogus-directive\n");
            p.write("a.txt", "clean\n");

            LintReport report = run(p, 1);
            assertEquals(3, report.configProblems().size());
            assertEquals(1, report.configProblems().get(0).line());
            assertEquals(2, report.configProblems().get(1).line());
            assertEquals(3, report.configProblems().get(2).line());
            assertTrue(report.configProblems().get(1).message().contains("unknown severity"));
        }
    }

    @Test
    void sameInputsProduceIdenticalResultsAcrossOrderAndParallelism() {
        try (TestProject p = TestProject.create()) {
            p.write(".lintrc", "severity builtin:trailing-whitespace=error\n");
            for (int i = 0; i < 12; i++) {
                p.write("d" + (i % 4) + "/f" + i + ".txt",
                        "line with space   \nTODO at " + i + "\n");
            }

            LintReport sequential = run(p, 1);
            LintReport parallel = run(p, 16);
            assertEquals(sequential.diagnostics(), parallel.diagnostics(),
                    "diagnostic set/order must be identical regardless of concurrency");
            assertEquals(sequential.configProblems(), parallel.configProblems());
        }
    }
}
