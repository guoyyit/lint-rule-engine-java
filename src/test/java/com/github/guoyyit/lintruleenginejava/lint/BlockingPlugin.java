package com.github.guoyyit.lintruleenginejava.lint;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** A plugin whose rule blocks on a latch until released or interrupted. */
public class BlockingPlugin implements LintRulePlugin {

    private final CountDownLatch latch;

    public BlockingPlugin(CountDownLatch latch) {
        this.latch = latch;
    }

    @Override public String getId() { return "test-blocking"; }
    @Override public String getName() { return "Blocking"; }
    @Override public String getVersion() { return "1.0"; }

    @Override public List<LintRule> getRules() {
        return List.of(new LintRule() {
            @Override public String getId() { return "blocking-rule"; }
            @Override public String getName() { return "Blocking Rule"; }
            @Override public String getDescription() { return "Blocks until released."; }
            @Override public Set<String> getApplicableExtensions() { return Set.of("txt"); }
            @Override public List<Diagnostic> check(SourceFile file) {
                if (file.path().getFileName().toString().startsWith("blocking")) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return List.of();
            }
        });
    }
}
