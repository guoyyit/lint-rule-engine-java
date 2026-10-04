package com.github.guoyyit.lintruleenginejava.model;

/**
 * A single-character-range replacement for one file.
 *
 * @param startOffset 0-based inclusive start offset within the file content
 * @param endOffset   0-based exclusive end offset within the file content
 * @param replacement text to put in place of the original range
 */
public record Fix(int startOffset, int endOffset, String replacement) implements Comparable<Fix> {

    public Fix {
        if (startOffset < 0 || endOffset < startOffset) {
            throw new IllegalArgumentException("invalid fix range");
        }
        if (replacement == null) {
            replacement = "";
        }
    }

    public boolean overlaps(Fix other) {
        return this.startOffset < other.endOffset && other.startOffset < this.endOffset;
    }

    @Override
    public int compareTo(Fix o) {
        int c = Integer.compare(this.startOffset, o.startOffset);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(this.endOffset, o.endOffset);
        if (c != 0) {
            return c;
        }
        return this.replacement.compareTo(o.replacement);
    }
}
