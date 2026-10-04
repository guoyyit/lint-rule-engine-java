package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Fix;
import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.Range;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.model.TextEdit;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Two fixable rules that both rewrite the same spot with different text. */
public class ConflictPlugin implements LintRulePlugin {

    @Override public String getId() { return "test-conflict"; }
    @Override public String getName() { return "Conflict"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(
                rule("conflict-a", "AAA"),
                rule("conflict-b", "BBB"));
    }

    private LintRule rule(String ruleId, String replacement) {
        return new LintRule() {
            @Override public String getId() { return ruleId; }
            @Override public String getName() { return ruleId; }
            @Override public String getDescription() { return "Replaces CONFLICT with " + replacement; }
            @Override public Severity getDefaultSeverity() { return Severity.WARNING; }
            @Override public Set<String> getApplicableExtensions() { return Set.of("txt"); }
            @Override public boolean isFixable() { return true; }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String[] lines = file.content().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    int idx = lines[i].indexOf("CONFLICT");
                    if (idx >= 0) {
                        Range range = new Range(
                                new Position(i + 1, idx + 1),
                                new Position(i + 1, idx + 1 + "CONFLICT".length()));
                        Fix fix = new Fix("replace with " + replacement,
                                List.of(new TextEdit(range, replacement)));
                        result.add(Diagnostic.lint(ruleId, Severity.WARNING, file.path(),
                                range, "conflict marker", fix));
                    }
                }
                return result;
            }

            @Override public Optional<Fix> fix(SourceFile file, Diagnostic diagnostic) {
                return diagnostic.fixOpt();
            }
        };
    }
}
