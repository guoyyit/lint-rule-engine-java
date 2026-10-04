package com.github.guoyyit.lintruleenginejava.tests.fixtures;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

import java.util.List;

/** Rule that sleeps (interruptibly) when the file name contains "slow". */
public final class SlowPlugin implements LintPlugin {

    public static final RuleId ID = RuleId.of("slow", "sleeper");

    @Override
    public String name() {
        return "slow";
    }

    @Override
    public List<Rule> rules() {
        return List.of(new Rule() {
            @Override
            public RuleId id() {
                return ID;
            }

            @Override
            public Severity defaultSeverity() {
                return Severity.WARNING;
            }

            @Override
            public boolean canFix() {
                return false;
            }

            @Override
            public String appliesTo() {
                return "**/*.txt";
            }

            @Override
            public void check(RuleContext ctx) {
                if (ctx.relativePath().toString().contains("slow")) {
                    long deadline = System.currentTimeMillis() + 2_000;
                    while (System.currentTimeMillis() < deadline) {
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                ctx.report(ID, 1, 1, Severity.WARNING, "slow rule ran on " + ctx.relativePath());
            }
        });
    }
}
