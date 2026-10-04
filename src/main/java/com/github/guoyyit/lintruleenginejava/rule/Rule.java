package com.github.guoyyit.lintruleenginejava.rule;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

/**
 * A single inspection rule, contributed by a {@code LintPlugin}.
 * Implementations must be thread-safe and stateless apart from configuration.
 */
public interface Rule {

    /** Unique rule identity (plugin name is provided by the owning plugin). */
    RuleId id();

    /** Declared default severity; directory configuration may override it. */
    Severity defaultSeverity();

    /** Whether this rule can produce automatic fixes. */
    boolean canFix();

    /** Glob-like file pattern relative to the analysis root, e.g. {@code **}{@code /*.txt}. */
    String appliesTo();

    /** Run the check against {@code ctx}; must not throw for ordinary input problems. */
    void check(RuleContext ctx);
}
