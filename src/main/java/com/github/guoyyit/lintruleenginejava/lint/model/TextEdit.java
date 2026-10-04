package com.github.guoyyit.lintruleenginejava.lint.model;

/**
 * A single text edit: replace the text in {@code range} with {@code newText}
 * (empty newText means deletion).
 */
public record TextEdit(Range range, String newText) implements Comparable<TextEdit> {
    @Override
    public int compareTo(TextEdit other) {
        int c = this.range.compareTo(other.range);
        return c != 0 ? c : this.newText.compareTo(other.newText);
    }
}
