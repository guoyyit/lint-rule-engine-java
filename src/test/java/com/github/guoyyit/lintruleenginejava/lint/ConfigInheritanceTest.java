package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.config.LintConfig;
import com.github.guoyyit.lintruleenginejava.lint.config.RuleConfig;
import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests directory-level config inheritance and override:
 * child configs inherit and override parent configs; siblings are unaffected.
 */
class ConfigInheritanceTest {

    private LintEngine engine(Path root) {
        return LintEngine.builder(PluginRegistry.load()).root(root).build();
    }

    private List<Diagnostic> diagnostics(Path root, Path file) {
        FileCheckResult result = engine(root).checkOne(file);
        assertEquals(FileCheckResult.Status.OK, result.status(), result.errorMessage());
        return result.diagnostics();
    }

    @Test
    void childSeverityOverridesParentButSiblingKeepsParent(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve(".lintrc.json"), """
                { "rules": { "no-todo": { "severity": "error" } } }
                """);
        Path sub = Files.createDirectories(root.resolve("sub"));
        Path sibling = Files.createDirectories(root.resolve("sibling"));
        Files.writeString(sub.resolve(".lintrc.json"), """
                { "rules": { "no-todo": { "severity": "warning" } } }
                """);
        Path subFile = sub.resolve("a.txt");
        Path siblingFile = sibling.resolve("b.txt");
        Files.writeString(subFile, "TODO x\n");
        Files.writeString(siblingFile, "TODO y\n");

        List<Diagnostic> subDiagnostics = diagnostics(root, subFile);
        List<Diagnostic> siblingDiagnostics = diagnostics(root, siblingFile);

        assertEquals(1, subDiagnostics.size());
        assertEquals(Severity.WARNING, subDiagnostics.get(0).severity());
        assertEquals(1, siblingDiagnostics.size());
        assertEquals(Severity.ERROR, siblingDiagnostics.get(0).severity());
    }

    @Test
    void childCanDisableRuleAndSiblingStaysEnabled(@TempDir Path root) throws Exception {
        Path sub = Files.createDirectories(root.resolve("sub"));
        Path sibling = Files.createDirectories(root.resolve("sibling"));
        Files.writeString(sub.resolve(".lintrc.json"), """
                { "rules": { "trailing-whitespace": { "enabled": false } } }
                """);
        Path subFile = sub.resolve("a.txt");
        Path siblingFile = sibling.resolve("b.txt");
        Files.writeString(subFile, "trailing   \n");
        Files.writeString(siblingFile, "trailing   \n");

        List<Diagnostic> subDiagnostics = diagnostics(root, subFile);
        List<Diagnostic> siblingDiagnostics = diagnostics(root, siblingFile);

        assertFalse(subDiagnostics.stream().anyMatch(d -> d.ruleId().equals("trailing-whitespace")),
                "rule should be disabled in subdir");
        assertTrue(siblingDiagnostics.stream().anyMatch(d -> d.ruleId().equals("trailing-whitespace")),
                "rule should still be enabled in sibling");
    }

    @Test
    void grandchildInheritsMergedConfig(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve(".lintrc.json"), """
                { "rules": { "no-todo": { "severity": "error" } } }
                """);
        Path sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve(".lintrc.json"), """
                { "rules": { "trailing-whitespace": { "enabled": false } } }
                """);
        Path grandchild = Files.createDirectories(sub.resolve("deep"));
        Path file = grandchild.resolve("a.txt");
        Files.writeString(file, "TODO x   \n");

        List<Diagnostic> diagnostics = diagnostics(root, file);
        assertEquals(1, diagnostics.size());
        assertEquals("no-todo", diagnostics.get(0).ruleId());
        assertEquals(Severity.ERROR, diagnostics.get(0).severity());
    }

    @Test
    void programmaticGlobalConfigIsOverriddenByDirectoryConfig(@TempDir Path root) throws Exception {
        Path sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve(".lintrc.json"), """
                { "rules": { "no-todo": { "severity": "error" } } }
                """);
        Path file = sub.resolve("a.txt");
        Files.writeString(file, "TODO x\n");

        LintConfig global = new LintConfig(
                java.util.Map.of("no-todo", new RuleConfig(null, Severity.INFO)),
                java.util.Set.of(), java.util.Set.of());
        LintEngine engine = LintEngine.builder(PluginRegistry.load())
                .root(root).globalConfig(global).build();
        List<Diagnostic> diagnostics = engine.checkOne(file).diagnostics();
        assertEquals(1, diagnostics.size());
        assertEquals(Severity.ERROR, diagnostics.get(0).severity(),
                "directory config must override global config");
    }

    @Test
    void malformedConfigFailsWithLocatableMessage(@TempDir Path root) throws Exception {
        Path sub = Files.createDirectories(root.resolve("sub"));
        Files.writeString(sub.resolve(".lintrc.json"), "{ not json");
        Path file = sub.resolve("a.txt");
        Files.writeString(file, "TODO x\n");

        LintEngine engine = engine(root);
        FileCheckResult result = engine.checkOne(file);
        // A broken config must not crash the check; the file is reported as failed.
        assertEquals(FileCheckResult.Status.FAILED, result.status());
        assertTrue(result.errorMessage().contains(".lintrc.json"));
    }
}
