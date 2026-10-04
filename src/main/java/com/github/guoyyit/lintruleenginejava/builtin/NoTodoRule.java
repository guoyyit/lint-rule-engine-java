package com.github.guoyyit.lintruleenginejava.builtin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;
import com.github.guoyyit.lintruleenginejava.util.LineIndex;

/**
 * Reports lines containing a {@code TODO} marker (word-boundary matched).
 * Intentionally not auto-fixable.
 */
public final class NoTodoRule implements Rule {

    public static final RuleId ID = RuleId.of(BuiltinPlugin.NAME, "no-todo");

    @Override
    public RuleId id() {
        return ID;
    }

    @Override
    public Severity defaultSeverity() {
        return Severity.INFO;
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
        String content = ctx.content();
        LineIndex index = LineIndex.of(content);
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int at = indexOfTodo(line);
            if (at >= 0) {
                int lineNo = i + 1;
                ctx.report(ID, lineNo, at + 1, Severity.INFO,
                        "TODO comment should be tracked in the issue tracker", null);
            }
        }
    }

    private static int indexOfTodo(String line) {
        for (int i = 0; i + 4 <= line.length(); i++) {
            if (line.regionMatches(true, i, "TODO", 0, 4)
                    && (i == 0 || !isWord(line.charAt(i - 1)))
                    && (i + 4 == line.length() || !isWord(line.charAt(i + 4)))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
