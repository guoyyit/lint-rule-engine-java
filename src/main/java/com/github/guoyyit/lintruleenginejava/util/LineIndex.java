package com.github.guoyyit.lintruleenginejava.util;

import java.util.Arrays;

/**
 * Maps between 1-based line/column positions and 0-based character offsets
 * for one piece of text. Line endings {@code \n}, {@code \r} and
 * {@code \r\n} are all recognized.
 */
public final class LineIndex {

    private final int[] lineStarts;
    private final int length;

    private LineIndex(int[] lineStarts, int length) {
        this.lineStarts = lineStarts;
        this.length = length;
    }

    public static LineIndex of(String content) {
        if (content == null) {
            content = "";
        }
        int[] starts = new int[Math.max(2, content.length() / 16)];
        int count = 0;
        starts[count++] = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '\n') {
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, starts.length * 2);
                }
                starts[count++] = i + 1;
            } else if (c == '\r') {
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, starts.length * 2);
                }
                starts[count++] = (i + 1 < content.length() && content.charAt(i + 1) == '\n')
                        ? i + 2 : i + 1;
                if (starts[count - 1] == i + 2) {
                    i++;
                }
            }
        }
        return new LineIndex(Arrays.copyOf(starts, count), content.length());
    }

    public int lineCount() {
        return lineStarts.length;
    }

    /** 0-based offset of the start of the given 1-based line. */
    public int offsetOfLine(int line) {
        if (line < 1 || line > lineStarts.length) {
            throw new IllegalArgumentException("line out of range: " + line);
        }
        return lineStarts[line - 1];
    }

    /** 1-based line containing the given 0-based offset. */
    public int lineAtOffset(int offset) {
        if (offset < 0 || offset > length) {
            throw new IllegalArgumentException("offset out of range: " + offset);
        }
        int lo = 0;
        int hi = lineStarts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (lineStarts[mid] <= offset) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo + 1;
    }

    /** 0-based column of the given 0-based offset within its line. */
    public int columnAtOffset(int offset) {
        return offset - lineStarts[lineAtOffset(offset) - 1];
    }
}
