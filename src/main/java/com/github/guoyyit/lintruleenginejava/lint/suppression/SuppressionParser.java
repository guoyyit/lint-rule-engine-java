package com.github.guoyyit.lintruleenginejava.lint.suppression;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.Range;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses inline suppression markers and applies them to diagnostics.
 *
 * <p>Supported markers (a comment prefix such as {@code //}, {@code #},
 * {@code <!--}, {@code --} or {@code ;} may precede the marker; the marker
 * may also stand alone at the beginning of a line):
 * <ul>
 *   <li>{@code lint-disable [r1, r2]} — disable rules until EOF or {@code lint-enable}</li>
 *   <li>{@code lint-enable [r1, r2]} — re-enables rules</li>
 *   <li>{@code lint-disable-line [r1, r2]} — disable on the current line</li>
 *   <li>{@code lint-disable-next-line [r1, r2]} — disable on the next line</li>
 * </ul>
 * Rules are given as a comma-separated list; an empty list means all rules.
 *
 * <p>Markers referencing unknown rules or being malformed produce locatable
 * {@link Diagnostic.Category#SUPPRESSION_HINT} diagnostics. Hints are never
 * suppressed themselves.
 */
public final class SuppressionParser {

    /** Rule id used for suppression hint diagnostics. */
    public static final String HINT_RULE_ID = "lint-engine:suppression";

    private static final String RULE_ID_PATTERN = "[A-Za-z0-9][A-Za-z0-9:._-]*";

    // A marker preceded by a comment prefix, anywhere in the line.
    private static final Pattern PREFIXED_MARKER = Pattern.compile(
            "(?://|#|<!--|--|;)\\s*lint-(disable-next-line|disable-line|disable|enable)\\b[\\t ]*(.*)",
            Pattern.CASE_INSENSITIVE);

    // A bare marker at the beginning of a line.
    private static final Pattern BARE_MARKER = Pattern.compile(
            "^\\s*lint-(disable-next-line|disable-line|disable|enable)\\b[\\t ]*(.*)",
            Pattern.CASE_INSENSITIVE);

    // Loose mention of lint+disable/enable, used to detect malformed markers.
    private static final Pattern LOOSE_MARKER = Pattern.compile(
            "lint[\\s\\-_]*(disable|disbale|diable|disble|enable|enble)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final String ALL = "*";

    private SuppressionParser() {
    }

    private enum Kind { DISABLE, ENABLE, DISABLE_LINE, DISABLE_NEXT_LINE }

    private record Marker(int line, Kind kind, List<String> rules, boolean malformed, String raw) {
    }

    private record Interval(int startLine, int endLine, Set<String> rules) {
        boolean covers(int line, String ruleId) {
            if (line < startLine || line > endLine) {
                return false;
            }
            return rules.contains(ALL) || rules.contains(ruleId);
        }
    }

    /**
     * Applies suppression to the given diagnostics for a file.
     *
     * @param file         the checked file
     * @param diagnostics  raw diagnostics from all rules
     * @param knownRuleIds ids of all registered rules (to validate marker references)
     * @return filtered diagnostics (suppressed ones removed) plus suppression hints
     */
    public static List<Diagnostic> apply(SourceFile file,
                                         List<Diagnostic> diagnostics,
                                         Set<String> knownRuleIds) {
        String[] lines = file.content().split("\n", -1);
        List<Marker> markers = new ArrayList<>();
        List<Diagnostic> hints = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i];
            Optional<Matcher> matcher = matcherFor(line);
            if (matcher.isPresent()) {
                Matcher m = matcher.get();
                Kind kind = Kind.valueOf(m.group(1).toUpperCase().replace('-', '_'));
                List<String> rules = parseRules(m.group(2));
                markers.add(new Marker(lineNo, kind, rules, false, line));
                for (String rule : rules) {
                    if (!knownRuleIds.contains(rule)) {
                        hints.add(hint(file, lineNo,
                                "Unknown rule '" + rule + "' referenced in suppression marker"));
                    }
                }
            } else {
                Matcher loose = LOOSE_MARKER.matcher(line);
                if (loose.find()) {
                    markers.add(new Marker(lineNo, null, List.of(), true, line));
                    hints.add(hint(file, lineNo,
                            "Malformed suppression marker '" + loose.group()
                                    + "' (expected lint-disable[-next-line|-line] or lint-enable)"));
                }
            }
        }

        // Compute suppression scopes.
        List<Interval> intervals = new ArrayList<>();
        Map<Integer, Set<String>> lineRules = new HashMap<>();
        Integer openAllStart = null;
        Map<String, Integer> openRuleStarts = new HashMap<>();

        for (Marker marker : markers) {
            if (marker.malformed) {
                continue;
            }
            switch (marker.kind) {
                case DISABLE -> {
                    if (marker.rules.isEmpty()) {
                        if (openAllStart == null) {
                            openAllStart = marker.line;
                        }
                    } else {
                        for (String rule : marker.rules) {
                            openRuleStarts.putIfAbsent(rule, marker.line);
                        }
                    }
                }
                case ENABLE -> {
                    int endLine = marker.line - 1;
                    if (marker.rules.isEmpty()) {
                        if (openAllStart != null) {
                            intervals.add(new Interval(openAllStart, endLine, Set.of(ALL)));
                            openAllStart = null;
                        }
                        for (Map.Entry<String, Integer> e : openRuleStarts.entrySet()) {
                            intervals.add(new Interval(e.getValue(), endLine, Set.of(e.getKey())));
                        }
                        openRuleStarts.clear();
                    } else {
                        for (String rule : marker.rules) {
                            Integer start = openRuleStarts.remove(rule);
                            if (start != null) {
                                intervals.add(new Interval(start, endLine, Set.of(rule)));
                            }
                        }
                    }
                }
                case DISABLE_LINE -> addLineRule(lineRules, marker.line, marker.rules);
                case DISABLE_NEXT_LINE -> addLineRule(lineRules, marker.line + 1, marker.rules);
            }
        }
        int lastLine = lines.length;
        if (openAllStart != null) {
            intervals.add(new Interval(openAllStart, lastLine, Set.of(ALL)));
        }
        for (Map.Entry<String, Integer> e : openRuleStarts.entrySet()) {
            intervals.add(new Interval(e.getValue(), lastLine, Set.of(e.getKey())));
        }

        List<Diagnostic> result = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            int line = d.range().start().line();
            String ruleId = d.ruleId();
            Set<String> rulesOnLine = lineRules.getOrDefault(line, Set.of());
            if (rulesOnLine.contains(ALL) || rulesOnLine.contains(ruleId)) {
                continue;
            }
            boolean suppressed = false;
            for (Interval interval : intervals) {
                if (interval.covers(line, ruleId)) {
                    suppressed = true;
                    break;
                }
            }
            if (!suppressed) {
                result.add(d);
            }
        }
        result.addAll(hints);
        return result;
    }

    private static Optional<Matcher> matcherFor(String line) {
        Matcher prefixed = PREFIXED_MARKER.matcher(line);
        if (prefixed.find()) {
            return Optional.of(prefixed);
        }
        Matcher bare = BARE_MARKER.matcher(line);
        if (bare.find()) {
            return Optional.of(bare);
        }
        return Optional.empty();
    }

    private static List<String> parseRules(String text) {
        List<String> rules = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return rules;
        }
        // Comma-separated; only the leading token of each part is treated as a
        // rule id, so trailing prose after the marker cannot cause false hints.
        String[] parts = text.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            Matcher m = Pattern.compile(RULE_ID_PATTERN).matcher(trimmed);
            if (m.lookingAt()) {
                rules.add(m.group());
            }
        }
        return rules;
    }

    private static void addLineRule(Map<Integer, Set<String>> lineRules, int line, List<String> rules) {
        lineRules.computeIfAbsent(line, k -> new HashSet<>())
                .addAll(rules.isEmpty() ? Set.of(ALL) : rules);
    }

    private static Diagnostic hint(SourceFile file, int line, String message) {
        Range range = new Range(new Position(line, 1), new Position(line, 1));
        return Diagnostic.suppressionHint(HINT_RULE_ID, Severity.WARNING, file.path(), range, message);
    }
}
