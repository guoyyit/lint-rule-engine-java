package com.github.guoyyit.lintruleenginejava.tests.fixtures;

import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

import java.util.List;

/** Public plugin fixtures used by the loader tests (referenced by class name). */
public final class TestPlugins {

    private TestPlugins() {
    }

    /** A healthy plugin contributing one fixable rule. */
    public static final class GoodPlugin implements LintPlugin {
        public static final RuleId RULE = RuleId.of("good", "alpha");

        @Override
        public String name() {
            return "good";
        }

        @Override
        public List<Rule> rules() {
            return List.of(new Rule() {
                @Override
                public RuleId id() {
                    return RULE;
                }

                @Override
                public Severity defaultSeverity() {
                    return Severity.WARNING;
                }

                @Override
                public boolean canFix() {
                    return true;
                }

                @Override
                public String appliesTo() {
                    return "**/*";
                }

                @Override
                public void check(RuleContext ctx) {
                }
            });
        }
    }

    /** Constructor always throws: must be reported, not crash loading. */
    public static final class ExplodingPlugin implements LintPlugin {
        public ExplodingPlugin() {
            throw new IllegalStateException("boom from constructor");
        }

        @Override
        public String name() {
            return "exploding";
        }

        @Override
        public List<Rule> rules() {
            return List.of();
        }
    }

    /** Declares a blank plugin name. */
    public static final class BlankNamePlugin implements LintPlugin {
        @Override
        public String name() {
            return "   ";
        }

        @Override
        public List<Rule> rules() {
            return List.of();
        }
    }

    /** Rules list construction throws. */
    public static final class RulesThrowingPlugin implements LintPlugin {
        @Override
        public String name() {
            return "rules-throwing";
        }

        @Override
        public List<Rule> rules() {
            throw new UnsupportedOperationException("cannot enumerate");
        }
    }

    /** Contributes a rule whose plugin prefix does not match its owner. */
    public static final class WrongRulePlugin implements LintPlugin {
        @Override
        public String name() {
            return "wrong";
        }

        @Override
        public List<Rule> rules() {
            return List.of(new Rule() {
                @Override
                public RuleId id() {
                    return RuleId.of("someone-else", "beta");
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
                    return "**/*";
                }

                @Override
                public void check(RuleContext ctx) {
                }
            });
        }
    }
}
