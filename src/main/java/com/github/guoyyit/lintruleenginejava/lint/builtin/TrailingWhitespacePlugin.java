package com.github.guoyyit.lintruleenginejava.lint.builtin;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Fix;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.model.TextEdit;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Flags trailing whitespace at end of lines; auto-fixable. */
public class TrailingWhitespacePlugin implements LintRulePlugin {

    @Override public String getId() { return "builtin-trailing-whitespace"; }
    @Override public String getName() { return "Trailing Whitespace"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "trailing-whitespace"; }
            @Override public String getName() { return "Trailing Whitespace"; }
            @Override public String getDescription() { return "Lines must not end with trailing whitespace."; }
            @Override public Severity getDefaultSeverity() { return Severity.INFO; }
            @Override public Set<String> getApplicableExtensions() { return Set.of(); }
            @Override public boolean isFixable() { return true; }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String[] lines = Ranges.lines(file.content());
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int trimmed = line.stripTrailing().length();
                    if (trimmed < line.length()) {
                        int fromColumn = trimmed + 1;
                        var range = Ranges.tailRange(i + 1, fromColumn, line);
                        Fix fix = new Fix("Remove trailing whitespace",
                                List.of(new TextEdit(range, "")));
                        result.add(Diagnostic.lint(getId(), getDefaultSeverity(), file.path(),
                                range, "Line has trailing whitespace", fix));
                    }
                }
                return result;
            }

            @Override public Optional<Fix> fix(SourceFile file, Diagnostic diagnostic) {
                return diagnostic.fixOpt();
            }
        });
    }
}
