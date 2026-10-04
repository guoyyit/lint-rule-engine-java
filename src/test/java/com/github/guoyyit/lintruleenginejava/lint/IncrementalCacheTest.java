package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.engine.CheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests incremental checking: cache hits for unchanged files, invalidation on
 * content/config/rule-set changes, bounded cache size and result stability.
 */
class IncrementalCacheTest {

    private List<Path> threeFiles(Path root) throws Exception {
        Files.writeString(root.resolve("a.txt"), "TODO one\n");
        Files.writeString(root.resolve("b.txt"), "TODO two\n");
        Files.writeString(root.resolve("c.txt"), "TODO three\n");
        return List.of(root.resolve("a.txt"), root.resolve("b.txt"), root.resolve("c.txt"));
    }

    @Test
    void unchangedFilesAreServedFromCache(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();

        CheckResult first = engine.check(files);
        assertEquals(3, first.checkedFiles());
        assertEquals(0, first.cacheHits());

        CheckResult second = engine.check(files);
        assertEquals(0, second.checkedFiles());
        assertEquals(3, second.cacheHits());
        assertEquals(first.diagnostics().size(), second.diagnostics().size());
        for (int i = 0; i < first.diagnostics().size(); i++) {
            assertEquals(first.diagnostics().get(i).ruleId(), second.diagnostics().get(i).ruleId());
            assertEquals(first.diagnostics().get(i).range(), second.diagnostics().get(i).range());
        }
    }

    @Test
    void changedContentInvalidatesOnlyThatFile(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        engine.check(files);

        Files.writeString(root.resolve("b.txt"), "changed TODO\n");
        CheckResult result = engine.check(files);
        assertEquals(1, result.checkedFiles());
        assertEquals(2, result.cacheHits());
    }

    @Test
    void configChangeInvalidatesCache(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        engine.check(files);

        Files.writeString(root.resolve(".lintrc.json"), """
                { "rules": { "no-todo": { "enabled": false } } }
                """);
        CheckResult result = engine.check(files);
        assertEquals(3, result.checkedFiles(), "config change must invalidate all entries");
        assertEquals(0, result.cacheHits());
        assertTrue(result.diagnostics().stream().noneMatch(d -> d.ruleId().equals("no-todo")));
    }

    @Test
    void ruleSetChangeInvalidatesCache(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        engine.check(files);

        LintEngine different = LintEngine.builder(PluginRegistry.of(new ConflictPlugin()))
                .root(root).build();
        CheckResult result = different.check(files);
        assertEquals(3, result.checkedFiles(), "rule set change must invalidate all entries");
        assertEquals(0, result.cacheHits());
    }

    @Test
    void cacheIsBounded(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load())
                .root(root).cacheSize(2).build();
        engine.check(files);
        assertTrue(engine.getCache().size() <= 2,
                "cache must be bounded, was " + engine.getCache().size());
    }

    @Test
    void cachedAndFreshResultsAreIdentical(@TempDir Path root) throws Exception {
        List<Path> files = threeFiles(root);
        LintEngine engine = LintEngine.builder(PluginRegistry.load()).root(root).build();
        CheckResult fresh = engine.check(files);
        CheckResult cached = engine.check(files);

        List<String> freshKeys = fresh.diagnostics().stream()
                .map(d -> d.filePath() + ":" + d.range().start().line() + ":" + d.ruleId())
                .toList();
        List<String> cachedKeys = cached.diagnostics().stream()
                .map(d -> d.filePath() + ":" + d.range().start().line() + ":" + d.ruleId())
                .toList();
        assertEquals(freshKeys, cachedKeys);
    }
}
