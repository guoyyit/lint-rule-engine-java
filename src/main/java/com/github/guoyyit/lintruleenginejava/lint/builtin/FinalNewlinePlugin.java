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

/** Flags files that do not end with a newline; auto-fixable. */
public class FinalNewlinePlugin implements LintRulePlugin {

    @Override public String getId() { return "builtin-final-newline"; }
    @Override public String getName() { return "Final Newline"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "final-newline"; }
            @Override public String getName() { return "Final Newline"; }
            @Override public String getDescription() { return "Files must end with a newline."; }
            @Override public Severity getDefaultSeverity() { return Severity.INFO; }
            @Override public Set<String> getApplicableExtensions() { return Set.of(); }
            @Override public boolean isFixable() { return true; }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String content = file.content();
                if (!content.isEmpty() && !content.endsWith("\n")) {
                    String[] lines = Ranges.lines(content);
                    int lastLine = lines.length;
                    var range = Ranges.endOfLineRange(lastLine, lines[lastLine - 1]);
                    Fix fix = new Fix("Add final newline", List.of(new TextEdit(range, "\n")));
                    result.add(Diagnostic.lint(getId(), getDefaultSeverity(), file.path(),
                            range, "File does not end with a newline", fix));
                }
                return result;
            }

            @Override public Optional<Fix> fix(SourceFile file, Diagnostic diagnostic) {
                return diagnostic.fixOpt();
            }
        });
    }
}
