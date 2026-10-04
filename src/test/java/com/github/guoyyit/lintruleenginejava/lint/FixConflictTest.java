package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.fix.FixApplier;
import com.github.guoyyit.lintruleenginejava.lint.fix.FixResult;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests auto-fix: conflicting edits are resolved deterministically,
 * user changes are never overwritten, and fixes are verified by re-checking.
 */
class FixConflictTest {

    @Test
    void conflictingFixesAreResolvedDeterministically(@TempDir Path root) throws Exception {
        Path file = root.resolve("a.txt");
        String content = "before CONFLICT after\n";
        Files.writeString(file, content);

        LintEngine engine = LintEngine.builder(PluginRegistry.of(new ConflictPlugin()))
                .root(root).build();
        FileCheckResult check = engine.checkOne(file);
        assertEquals(FileCheckResult.Status.OK, check.status());
        assertEquals(2, check.diagnostics().size(), "both rules report the marker");

        FixApplier fixer = new FixApplier(engine);
        FixResult result = fixer.fix(file, check.diagnostics(), content);

        assertEquals(FixResult.Status.APPLIED, result.status());
        assertEquals(1, result.appliedEdits().size());
        assertEquals(1, result.conflictingEdits().size(), "losing edit is reported as conflicting");
        // conflict-a wins (lexicographic rule id priority).
        String newContent = Files.readString(file);
        assertEquals("before AAA after\n", newContent);
        assertTrue(result.remainingDiagnostics().stream()
                .noneMatch(d -> d.ruleId().startsWith("conflict")),
                "re-check must confirm the conflict marker is gone");
    }

    @Test
    void fixIsAbortedWhenContentChangedSinceCheck(@TempDir Path root) throws Exception {
        Path file = root.resolve("a.txt");
        String content = "before CONFLICT after\n";
        Files.writeString(file, content);

        LintEngine engine = LintEngine.builder(PluginRegistry.of(new ConflictPlugin()))
                .root(root).build();
        FileCheckResult check = engine.checkOne(file);

        // User edits the file after the check.
        Files.writeString(file, "user changed CONFLICT\n");

        FixApplier fixer = new FixApplier(engine);
        FixResult result = fixer.fix(file, check.diagnostics(), content);

        assertEquals(FixResult.Status.ABORTED_CONTENT_CHANGED, result.status());
        assertEquals("user changed CONFLICT\n", Files.readString(file),
                "user content must not be overwritten");
    }

    @Test
    void nonConflictingFixAppliesAndRecheckIsClean(@TempDir Path root) throws Exception {
        Path file = root.resolve("a.txt");
        String content = "trailing   \n";
        Files.writeString(file, content);

        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        FileCheckResult check = engine.checkOne(file);
        assertTrue(check.diagnostics().stream()
                .anyMatch(d -> d.ruleId().equals("trailing-whitespace")));

        FixApplier fixer = new FixApplier(engine);
        FixResult result = fixer.fix(file, check.diagnostics(), content);

        assertEquals(FixResult.Status.APPLIED, result.status());
        assertEquals("trailing\n", Files.readString(file));
        assertTrue(result.remainingDiagnostics().stream()
                .noneMatch(d -> d.ruleId().equals("trailing-whitespace")),
                "re-check must confirm the issue is gone");
    }

    @Test
    void noFixableDiagnosticsYieldsNoFixes(@TempDir Path root) throws Exception {
        Path file = root.resolve("a.txt");
        String content = "TODO x\n";
        Files.writeString(file, content);

        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        FileCheckResult check = engine.checkOne(file);
        FixApplier fixer = new FixApplier(engine);
        FixResult result = fixer.fix(file, check.diagnostics(), content);

        assertEquals(FixResult.Status.NO_FIXES, result.status());
        assertEquals(content, Files.readString(file));
    }

    @Test
    void conflictResolutionIsStableAcrossReruns(@TempDir Path root) throws Exception {
        Path file = root.resolve("a.txt");
        String content = "CONFLICT\n";
        Files.writeString(file, content);

        LintEngine engine = LintEngine.builder(PluginRegistry.of(new ConflictPlugin()))
                .root(root).build();
        FixApplier fixer = new FixApplier(engine);

        String winner = null;
        for (int i = 0; i < 5; i++) {
            Files.writeString(file, content);
            FileCheckResult check = engine.checkOne(file);
            FixResult result = fixer.fix(file, check.diagnostics(), content);
            assertEquals(FixResult.Status.APPLIED, result.status());
            String newContent = Files.readString(file);
            if (winner == null) {
                winner = newContent;
            } else {
                assertEquals(winner, newContent, "fix result must be deterministic");
            }
        }
        assertEquals("AAA\n", winner);
    }
}
