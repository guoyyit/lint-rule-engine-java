package com.github.guoyyit.lintruleenginejava.lint.builtin;

import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.Range;

/**
 * Package-private helpers for building {@link Range}s from line-based content.
 */
final class Ranges {

    private Ranges() {
    }

    /** Splits content into lines (line content without the trailing newline). */
    static String[] lines(String content) {
        return content.split("\n", -1);
    }

    /** Range covering the whole given line (columns 1 .. line length + 1). */
    static Range lineRange(int line, String lineContent) {
        return new Range(new Position(line, 1), new Position(line, lineContent.length() + 1));
    }

    /** Range covering columns {@code fromColumn} .. end of the line. */
    static Range tailRange(int line, int fromColumn, String lineContent) {
        return new Range(new Position(line, fromColumn),
                new Position(line, lineContent.length() + 1));
    }

    /** Zero-length range at the end of the given line. */
    static Range endOfLineRange(int line, String lineContent) {
        return new Range(new Position(line, lineContent.length() + 1),
                new Position(line, lineContent.length() + 1));
    }
}
