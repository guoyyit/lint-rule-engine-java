package com.github.guoyyit.lintruleenginejava.lint.builtin;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Fix;
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

/** Flags leading tab indentation; auto-fixable (tab -> 4 spaces). */
public class NoTabIndentPlugin implements LintRulePlugin {

    @Override public String getId() { return "builtin-no-tab-indent"; }
    @Override public String getName() { return "No Tab Indent"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "no-tab-indent"; }
            @Override public String getName() { return "No Tab Indent"; }
            @Override public String getDescription() { return "Indentation must use spaces, not tabs."; }
            @Override public Severity getDefaultSeverity() { return Severity.WARNING; }
            @Override public Set<String> getApplicableExtensions() { return Set.of(); }
            @Override public boolean isFixable() { return true; }

            @Override public List<Diagnostic> check(SourceFile file) {
                List<Diagnostic> result = new ArrayList<>();
                String[] lines = Ranges.lines(file.content());
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    int leading = 0;
                    while (leading < line.length() && line.charAt(leading) == '\t') {
                        leading++;
                    }
                    if (leading > 0) {
                        Range range = new Range(
                                new com.github.guoyyit.lintruleenginejava.lint.model.Position(i + 1, 1),
                                new com.github.guoyyit.lintruleenginejava.lint.model.Position(i + 1, leading + 1));
                        String replacement = "    ".repeat(leading);
                        Fix fix = new Fix("Replace tab indentation with spaces",
                                List.of(new TextEdit(range, replacement)));
                        result.add(Diagnostic.lint(getId(), getDefaultSeverity(), file.path(),
                                range, "Indentation uses tabs", fix));
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
