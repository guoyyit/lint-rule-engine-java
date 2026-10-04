package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.Range;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.List;
import java.util.Set;

/** A plugin whose rule sleeps, used for timeout tests. */
public class SlowPlugin implements LintRulePlugin {

    private final long sleepMillis;

    public SlowPlugin(long sleepMillis) {
        this.sleepMillis = sleepMillis;
    }

    @Override public String getId() { return "test-slow"; }
    @Override public String getName() { return "Slow"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "slow-rule"; }
            @Override public String getName() { return "Slow Rule"; }
            @Override public String getDescription() { return "Sleeps instead of checking."; }
            @Override public Severity getDefaultSeverity() { return Severity.WARNING; }
            @Override public Set<String> getApplicableExtensions() { return Set.of("txt"); }
            @Override public List<Diagnostic> check(SourceFile file) {
                if (file.path().getFileName().toString().startsWith("slow")) {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return List.of(Diagnostic.lint("slow-rule", Severity.WARNING, file.path(),
                        new Range(new Position(1, 1), new Position(1, 1)), "slow", null));
            }
        });
    }
}
