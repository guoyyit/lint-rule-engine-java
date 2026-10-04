package com.github.guoyyit.lintruleenginejava.builtin;

import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.rule.Rule;
import com.github.guoyyit.lintruleenginejava.rule.RuleContext;
import com.github.guoyyit.lintruleenginejava.util.LineIndex;

/**
 * Reports lines ending with spaces or tabs; the auto-fix removes them.
 * Whitespace-only lines are reported as well and collapsed to an empty line.
 */
public final class TrailingWhitespaceRule implements Rule {

    public static final RuleId ID = RuleId.of(BuiltinPlugin.NAME, "trailing-whitespace");

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
        return "**/*";
    }

    @Override
    public void check(RuleContext ctx) {
        String content = ctx.content();
        LineIndex index = LineIndex.of(content);
        int lineCount = index.lineCount();
        for (int line = 1; line <= lineCount; line++) {
            int start = index.offsetOfLine(line);
            int end;
            if (line < lineCount) {
                end = index.offsetOfLine(line + 1);
            } else {
                end = content.length();
            }
            // Trim the newline characters at line end.
            while (end > start && (content.charAt(end - 1) == '\n' || content.charAt(end - 1) == '\r')) {
                end--;
            }
            // A trailing comment cannot itself be "trailing whitespace":
            // inspect only the code before the first // or # comment marker.
            int comment = firstComment(content, start, end);
            int scanEnd = comment >= 0 ? comment : end;
            int p = scanEnd;
            while (p > start && (content.charAt(p - 1) == ' ' || content.charAt(p - 1) == '\t')) {
                p--;
            }
            if (p < scanEnd) {
                int column = p - start + 1;
                ctx.report(ID, line, column, Severity.WARNING,
                        "trailing whitespace (" + (scanEnd - p) + " character(s))",
                        new Fix(p, scanEnd, ""));
            }
        }
    }

    private static int firstComment(String content, int from, int to) {
        for (int i = from; i + 1 < to; i++) {
            char c = content.charAt(i);
            if (c == '#' || (c == '/' && content.charAt(i + 1) == '/')) {
                return i;
            }
        }
        return -1;
    }
}
