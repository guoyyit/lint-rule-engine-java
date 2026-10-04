package com.github.guoyyit.lintruleenginejava.builtin;

import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.rule.Rule;

import java.util.List;

/** Built-in plugin contributing the rules shipped with the engine. */
public final class BuiltinPlugin implements LintPlugin {

    public static final String NAME = "builtin";

    @Override
    public String name() {
        // skeleton
        return null;
    }

    @Override
    public List<Rule> rules() {
        // skeleton
        return List.of();
    }
}
