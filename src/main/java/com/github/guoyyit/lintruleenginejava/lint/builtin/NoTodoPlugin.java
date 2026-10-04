package com.github.guoyyit.lintruleenginejava.lint.builtin;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Flags {@code TODO} markers left in the code. */
public class NoTodoPlugin implements LintRulePlugin {

    @Override public String getId() { return "builtin-no-todo"; }
    @Override public String getName() { return "No TODO"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "no-todo"; }
            @Override public String getName() { return "No TODO"; }
            @Override public String getDescription() { return "Flags TODO markers left in the code."; }
            @Override public Severity getDefaultSeverity() { return Severity.WARNING; }
            @Override public Set<String> getApplicableExtensions() { return Set.of(); }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String[] lines = Ranges.lines(file.content());
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].contains("TODO")) {
                        result.add(Diagnostic.lint(getId(), getDefaultSeverity(), file.path(),
                                Ranges.lineRange(i + 1, lines[i]),
                                "Found TODO marker", null));
                    }
                }
                return result;
            }
        });
    }
}
