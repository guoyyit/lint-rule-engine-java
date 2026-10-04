package com.github.guoyyit.lintruleenginejava.builtin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

/** Reports lines containing a {@code TODO} marker (not auto-fixable). */
public final class NoTodoRule implements Rule {

    public static final RuleId ID = RuleId.of(BuiltinPlugin.NAME, "no-todo");

    @Override
    public RuleId id() {
        return null;
    }

    @Override
    public Severity defaultSeverity() {
        return null;
    }

    @Override
    public boolean canFix() {
        return false;
    }

    @Override
    public String appliesTo() {
        return null;
    }

    @Override
    public void check(RuleContext ctx) {
    }
}
