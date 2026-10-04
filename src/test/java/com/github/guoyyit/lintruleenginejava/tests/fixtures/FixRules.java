package com.github.guoyyit.lintruleenginejava.tests.fixtures;

import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.plugin.LintPlugin;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;

import java.util.List;

/**
 * Two plugins that both fix the same range differently, to exercise the
 * deterministic conflict policy.
 */
public final class FixRules {

    private FixRules() {
    }

    static final String MARKER = "CONFLICT";

    /** Lower rule id: replaces marker with "low". */
    public static final class LowPlugin implements LintPlugin {
        public static final RuleId ID = RuleId.of("aaa-low", "same-range");

        @Override
        public String name() {
            return "aaa-low";
        }

        @Override
        public List<Rule> rules() {
            return List.of(new MarkerRule(ID, "low"));
        }
    }

    /** Higher rule id: replaces marker with "high" - must win. */
    public static final class HighPlugin implements LintPlugin {
        public static final RuleId ID = RuleId.of("zzz-high", "same-range");

        @Override
        public String name() {
            return "zzz-high";
        }

        @Override
        public List<Rule> rules() {
            return List.of(new MarkerRule(ID, "high"));
        }
    }

    /** Fixes a different, non-overlapping range: must always co-apply. */
    public static final class OtherPlugin implements LintPlugin {
        public static final RuleId ID = RuleId.of("mmm-other", "elsewhere");

        @Override
        public String name() {
            return "mmm-other";
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
                    return true;
                }

                @Override
                public String appliesTo() {
                    return "**/*.txt";
                }

                @Override
                public void check(RuleContext ctx) {
                    String c = ctx.content();
                    int at = c.indexOf("ELSE");
                    if (at >= 0) {
                        ctx.report(ID, 1, at + 1, Severity.WARNING, "else marker",
                                new Fix(at, at + 4, "done"));
                    }
                }
            });
        }
    }

    private static final class MarkerRule implements Rule {
        private final RuleId id;
        private final String replacement;

        MarkerRule(RuleId id, String replacement) {
            this.id = id;
            this.replacement = replacement;
        }

        @Override
        public RuleId id() {
            return id;
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
            return "**/*.txt";
        }

        @Override
        public void check(RuleContext ctx) {
            String c = ctx.content();
            int at = c.indexOf(MARKER);
            if (at >= 0) {
                ctx.report(id, 1, at + 1, Severity.WARNING,
                        "marker " + MARKER + " for " + id,
                        new Fix(at, at + MARKER.length(), replacement));
            }
        }
    }
}
