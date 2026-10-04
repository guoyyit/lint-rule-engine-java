package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.List;
import java.util.Set;

/** A plugin that throws while loading its rules. */
public class FailingPlugin implements LintRulePlugin {

    @Override public String getId() { return "test-failing"; }
    @Override public String getName() { return "Failing"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        throw new IllegalStateException("boom from getRules");
    }

    public static LintRulePlugin blankId() {
        return new LintRulePlugin() {
            @Override public String getId() { return "  "; }
            @Override public String getName() { return "Blank Id"; }
            @Override public String getVersion() { return "1.0"; }
            @Override public List<LintRule> getRules() { return List.of(); }
        };
    }

    public static LintRulePlugin ruleThrowingPlugin() {
        return new LintRulePlugin() {
            @Override public String getId() { return "test-rule-throw"; }
            @Override public String getName() { return "Rule Throw"; }
            @Override public String getVersion() { return "1.0"; }
            @Override public List<LintRule> getRules() {
                return List.of(new LintRule() {
                    @Override public String getId() { return "throw-rule"; }
                    @Override public String getName() { return "Throw Rule"; }
                    @Override public String getDescription() { return "Always throws."; }
                    @Override public Set<String> getApplicableExtensions() { return Set.of("txt"); }
                    @Override public List<Diagnostic> check(SourceFile file) {
                        throw new RuntimeException("rule boom");
                    }
                });
            }
        };
    }
}
