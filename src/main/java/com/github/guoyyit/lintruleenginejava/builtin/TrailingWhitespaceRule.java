package com.github.guoyyit.lintruleenginejava.builtin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

/** Reports lines ending with whitespace; fix trims the trailing spaces/tabs. */
public final class TrailingWhitespaceRule implements Rule {

    public static final RuleId ID = RuleId.of(BuiltinPlugin.NAME, "trailing-whitespace");

    @Override
    public RuleId id() {
        // skeleton
        return null;
    }

    @Override
    public Severity defaultSeverity() {
        // skeleton
        return null;
    }

    @Override
    public boolean canFix() {
        // skeleton
        return false;
    }

    @Override
    public String appliesTo() {
        // skeleton
        return null;
    }

    @Override
    public void check(RuleContext ctx) {
        // skeleton
    }
}
