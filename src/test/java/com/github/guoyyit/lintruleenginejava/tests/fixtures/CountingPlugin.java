package com.github.guoyyit.lintruleenginejava.tests.fixtures;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Rule counting how often it actually executed on a given file name. */
public final class CountingPlugin implements LintPlugin {

    public static final RuleId ID = RuleId.of("counter", "marker");
    public static final AtomicInteger EXECUTIONS = new AtomicInteger();

    @Override
    public String name() {
        return "counter";
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
                EXECUTIONS.incrementAndGet();
                if (ctx.content().contains("ZZZ")) {
                    ctx.report(ID, 1, 1, Severity.WARNING, "found ZZZ in " + ctx.relativePath());
                }
            }
        });
    }
}
