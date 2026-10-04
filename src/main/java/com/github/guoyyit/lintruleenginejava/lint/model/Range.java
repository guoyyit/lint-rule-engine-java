package com.github.guoyyit.lintruleenginejava.lint.model;

/**
 * A range in a source file, from {@code start} (inclusive) to {@code end} (exclusive).
 */
public record Range(Position start, Position end) implements Comparable<Range> {
    @Override
    public int compareTo(Range other) {
        int c = this.start.compareTo(other.start);
        return c != 0 ? c : this.end.compareTo(other.end);
    }
}
