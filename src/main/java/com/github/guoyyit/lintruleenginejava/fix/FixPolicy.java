package com.github.guoyyit.lintruleenginejava.fix;

/**
 * Strategy for choosing among overlapping fixes.
 */
public enum FixPolicy {
    /** Highest rule-id (plugin:rule, lexicographic) wins; fully deterministic. */
    HIGHEST_RULE_ID
}
