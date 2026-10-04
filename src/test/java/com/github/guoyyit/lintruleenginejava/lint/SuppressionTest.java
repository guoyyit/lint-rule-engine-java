package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests inline suppression: next-line, current-line, disable/enable blocks,
 * all-rules suppression, unknown-rule hints and malformed marker hints.
 */
class SuppressionTest {

    private LintEngine engine(Path root) {
        return LintEngine.builder(PluginRegistry.load()).root(root).build();
    }

    private List<Diagnostic> check(Path root, Path file) {
        FileCheckResult result = engine(root).checkOne(file);
        assertEquals(FileCheckResult.Status.OK, result.status(), result.errorMessage());
        return result.diagnostics();
    }

    @Test
    void nextLineAndLineAndBlockSuppression(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.txt");
        String content = """
                TODO plain
                int x = 1; // lint-disable-next-line no-todo
                TODO suppressed by next-line
                TODO same line // lint-disable-line no-todo
                // lint-disable no-todo
                TODO suppressed in block
                // lint-enable no-todo
                TODO active again
                """;
        Files.writeString(file, content);

        List<Diagnostic> diagnostics = check(root, file);
        List<Integer> todoLines = diagnostics.stream()
                .filter(d -> d.ruleId().equals("no-todo"))
                .map(d -> d.range().start().line())
                .toList();
        assertEquals(List.of(1, 8), todoLines, "only unsuppressed TODOs should remain");
    }

    @Test
    void allRulesSuppressionAndHashPrefix(@TempDir Path root) throws Exception {
        Path file = root.resolve("script.sh");
        String content = """
                # lint-disable
                TODO suppressed
                trailing spaces
                # lint-enable
                TODO active
                """;
        Files.writeString(file, content);

        List<Diagnostic> diagnostics = check(root, file);
        List<Integer> lines = diagnostics.stream()
                .map(d -> d.range().start().line())
                .toList();
        assertEquals(List.of(5), lines, "only the TODO after enable should remain");
    }

    @Test
    void unknownRuleReferenceProducesHint(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.txt");
        String content = """
                // lint-disable-next-line ghost-rule
                TODO not suppressed
                """;
        Files.writeString(file, content);

        List<Diagnostic> diagnostics = check(root, file);
        assertEquals(2, diagnostics.size());
        Diagnostic hint = diagnostics.stream()
                .filter(d -> d.category() == Diagnostic.Category.SUPPRESSION_HINT)
                .findFirst().orElseThrow();
        assertEquals(1, hint.range().start().line());
        assertTrue(hint.message().contains("ghost-rule"));
        assertTrue(hint.message().contains("Unknown rule"));
        // The TODO is still reported (ghost rule cannot suppress it).
        assertEquals(1, diagnostics.stream()
                .filter(d -> d.ruleId().equals("no-todo")).count());
    }

    @Test
    void malformedMarkerProducesHint(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.txt");
        String content = """
                // lint-disbale no-todo
                TODO active
                """;
        Files.writeString(file, content);

        List<Diagnostic> diagnostics = check(root, file);
        assertEquals(2, diagnostics.size());
        Diagnostic hint = diagnostics.stream()
                .filter(d -> d.category() == Diagnostic.Category.SUPPRESSION_HINT)
                .findFirst().orElseThrow();
        assertEquals(1, hint.range().start().line());
        assertTrue(hint.message().contains("Malformed"));
        // The malformed marker does not suppress anything.
        assertEquals(1, diagnostics.stream()
                .filter(d -> d.ruleId().equals("no-todo")).count());
    }

    @Test
    void hintsAreNeverSuppressed(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.txt");
        String content = """
                // lint-disable no-todo
                // lint-disbale no-todo
                """;
        Files.writeString(file, content);

        List<Diagnostic> diagnostics = check(root, file);
        // Suppression hints are engine-generated and cannot be suppressed.
        assertEquals(1, diagnostics.size());
        assertEquals(Diagnostic.Category.SUPPRESSION_HINT, diagnostics.get(0).category());
    }

    @Test
    void suppressionScopeIsDeterministicAcrossReruns(@TempDir Path root) throws Exception {
        Path file = root.resolve("sample.txt");
        String content = """
                TODO one
                // lint-disable-next-line no-todo
                TODO two
                // lint-disable no-todo
                TODO three
                // lint-enable no-todo
                TODO four
                """;
        Files.writeString(file, content);

        LintEngine engine = engine(root);
        List<Diagnostic> first = engine.checkOne(file).diagnostics();
        List<Diagnostic> second = engine.checkOne(file).diagnostics();
        assertEquals(first.stream().map(Diagnostic::ruleId).toList(),
                second.stream().map(Diagnostic::ruleId).toList());
        assertEquals(first.stream().map(d -> d.range().start().line()).toList(),
                second.stream().map(d -> d.range().start().line()).toList());
    }
}
