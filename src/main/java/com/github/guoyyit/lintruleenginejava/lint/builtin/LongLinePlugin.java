package com.github.guoyyit.lintruleenginejava.lint.builtin;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Flags lines longer than 120 characters. */
public class LongLinePlugin implements LintRulePlugin {

    static final int MAX_LINE_LENGTH = 120;

    @Override public String getId() { return "builtin-long-line"; }
    @Override public String getName() { return "Long Line"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "long-line"; }
            @Override public String getName() { return "Long Line"; }
            @Override public String getDescription() { return "Lines should not exceed " + MAX_LINE_LENGTH + " characters."; }
            @Override public Severity getDefaultSeverity() { return Severity.INFO; }
            @Override public Set<String> getApplicableExtensions() { return Set.of(); }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String[] lines = Ranges.lines(file.content());
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].length() > MAX_LINE_LENGTH) {
                        result.add(Diagnostic.lint(getId(), getDefaultSeverity(), file.path(),
                                Ranges.lineRange(i + 1, lines[i]),
                                "Line is " + lines[i].length() + " characters (max " + MAX_LINE_LENGTH + ")",
                                null));
                    }
                }
                return result;
            }
        });
    }
}
