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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests: directory walking, deterministic ordering and the
 * full check pipeline with built-in plugins.
 */
class LintEngineEndToEndTest {

    @Test
    void checkDirectoryWalksAndSortsDeterministically(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "TODO a\ntrailing   \n");
        Path sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve("b.txt"), "TODO b\n");
        Files.createDirectories(root.resolve("target"));
        Files.writeString(root.resolve("target").resolve("c.txt"), "TODO c\n");

        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        CheckResult result = engine.checkDirectory();

        // target/ is skipped; a.txt and sub/b.txt are checked.
        List<String> checkedFiles = result.fileResults().stream()
                .map(r -> r.file().getFileName().toString())
                .sorted()
                .toList();
        assertEquals(List.of("a.txt", "b.txt"), checkedFiles);

        // Diagnostics are stably sorted by file, then line, then rule id.
        List<String> keys = result.diagnostics().stream()
                .map(d -> d.filePath().getFileName() + ":" + d.range().start().line()
                        + ":" + d.ruleId())
                .toList();
        assertEquals(keys.stream().sorted().toList(), keys, "diagnostics must be sorted");
    }

    @Test
    void checkIsIdempotentAndOrderIndependent(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "TODO a\n");
        Files.writeString(root.resolve("b.txt"), "TODO b\n");

        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        CheckResult first = engine.checkDirectory();
        CheckResult second = engine.checkDirectory();

        assertEquals(first.diagnostics().size(), second.diagnostics().size());
        for (int i = 0; i < first.diagnostics().size(); i++) {
            Diagnostic a = first.diagnostics().get(i);
            Diagnostic b = second.diagnostics().get(i);
            assertEquals(a.filePath(), b.filePath());
            assertEquals(a.range(), b.range());
            assertEquals(a.ruleId(), b.ruleId());
            assertEquals(a.message(), b.message());
        }
    }

    @Test
    void allFilesOkWhenClean(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "clean content\n");
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        CheckResult result = engine.checkDirectory();
        assertTrue(result.fileResults().stream()
                .allMatch(r -> r.status() == FileCheckResult.Status.OK));
    }
}
