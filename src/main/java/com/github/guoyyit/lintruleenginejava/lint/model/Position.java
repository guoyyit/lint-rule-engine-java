package com.github.guoyyit.lintruleenginejava.lint.model;

/**
 * A position in a source file. Line and column are 1-based.
 */
public record Position(int line, int column) implements Comparable<Position> {
    @Override
    public int compareTo(Position other) {
        int c = Integer.compare(this.line, other.line);
        return c != 0 ? c : Integer.compare(this.column, other.column);
    }
}
