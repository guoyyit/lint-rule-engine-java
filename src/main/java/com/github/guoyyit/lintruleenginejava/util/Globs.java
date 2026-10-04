package com.github.guoyyit.lintruleenginejava.util;

import java.util.List;

/**
 * Small deterministic glob matcher for {@code /}-separated relative paths:
 * {@code ?} matches one character, {@code *} matches within a path segment,
 * {@code **} matches any number of segments (including zero). Matching is
 * case-sensitive.
 */
public final class Globs {

    private Globs() {
    }

    /** Compile and test one pattern. */
    public static boolean matches(String pattern, String path) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }
        String[] p = splitSegments(pattern);
        String[] s = splitSegments(path);
        return matchSegments(p, 0, s, 0);
    }

    /** A path matches if it matches at least one pattern; empty patterns match nothing. */
    public static boolean matchesAny(List<String> patterns, String path) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (String pattern : patterns) {
            if (matches(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitSegments(String path) {
        String norm = path.replace('\\', '/');
        while (norm.startsWith("/")) {
            norm = norm.substring(1);
        }
        return norm.split("/", -1);
    }

    private static boolean matchSegments(String[] p, int pi, String[] s, int si) {
        while (pi < p.length) {
            if ("**".equals(p[pi])) {
                // Collapse consecutive ** segments.
                while (pi < p.length && "**".equals(p[pi])) {
                    pi++;
                }
                if (pi == p.length) {
                    return true;
                }
                for (int k = si; k <= s.length; k++) {
                    if (matchSegments(p, pi, s, k)) {
                        return true;
                    }
                }
                return false;
            }
            if (si >= s.length) {
                return false;
            }
            if (!matchSegment(p[pi], s[si])) {
                return false;
            }
            pi++;
            si++;
        }
        return si == s.length;
    }

    private static boolean matchSegment(String pattern, String segment) {
        boolean[][] dp = new boolean[pattern.length() + 1][segment.length() + 1];
        dp[0][0] = true;
        for (int i = 1; i <= pattern.length(); i++) {
            char pc = pattern.charAt(i - 1);
            if (pc == '*') {
                dp[i][0] = dp[i - 1][0];
                for (int j = 1; j <= segment.length(); j++) {
                    dp[i][j] = dp[i - 1][j] || dp[i][j - 1];
                }
            } else if (pc == '?') {
                for (int j = 1; j <= segment.length(); j++) {
                    dp[i][j] = dp[i - 1][j - 1];
                }
            } else {
                for (int j = 1; j <= segment.length(); j++) {
                    dp[i][j] = dp[i - 1][j - 1] && pc == segment.charAt(j - 1);
                }
            }
        }
        return dp[pattern.length()][segment.length()];
    }
}
