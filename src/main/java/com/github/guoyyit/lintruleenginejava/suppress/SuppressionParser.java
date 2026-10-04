package com.github.guoyyit.lintruleenginejava.suppress;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses inline suppression markers from file content.
 *
 * <p>Markers live inside {@code //} or {@code #} comments and use these forms:
 * <ul>
 *   <li>{@code lint:suppress-all} — suppress every rule;</li>
 *   <li>{@code lint:suppress plugin:rule [plugin:rule ...]} — suppress rules;</li>
 *   <li>{@code lint:disable [plugin:rule ...]} / {@code lint:enable [...]} —
 *       a region from the marker line up to the line before the matching
 *       enable marker (or to end of file); no rules means every rule.</li>
 * </ul>
 * A marker on a code line applies to that line; a marker that is the only
 * content of its line applies to the following line as well.
 */
public final class SuppressionParser {

    /** Rule identity used for diagnostics that describe bad markers. */
    public static final RuleId MARKER_RULE = RuleId.of("lint", "bad-suppression");

    private static final Pattern DIRECTIVE = Pattern.compile(
            "(?<![A-Za-z0-9_:-])lint:(suppress-all|suppress|disable-all|disable|enable)\\b([^\\n]*)");
    private static final Pattern COMMENT = Pattern.compile("//|#");

    public SuppressionSet parse(String content, Path relativeFile, Set<RuleId> knownRules) {
        if (content == null) {
            content = "";
        }
        if (knownRules == null) {
            knownRules = Set.of();
        }
        String[] lines = content.split("\n", -1);
        Map<Integer, Set<RuleId>> single = new TreeMap<>();
        Set<Integer> suppressAll = new java.util.TreeSet<>();
        List<Region> regions = new ArrayList<>();
        List<Diagnostic> problems = new ArrayList<>();

        // Open region state: rules currently disabled (null set means "all").
        Set<RuleId> openAll = null;
        Set<RuleId> openRules = null;
        int openStart = -1;

        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String line = lines[i];
            Matcher comment = COMMENT.matcher(line);
            if (!comment.find()) {
                continue;
            }
            String commentText = line.substring(comment.start());
            boolean codeBeforeMarker = !line.substring(0, comment.start()).isBlank();
            Matcher m = DIRECTIVE.matcher(commentText);
            boolean foundOne = false;
            while (m.find()) {
                if (foundOne) {
                    problems.add(problem(relativeFile, lineNo,
                            "multiple lint directives on one line; only the first is honored", m.group()));
                    break;
                }
                foundOne = true;
                String kind = m.group(1);
                String args = m.group(2) == null ? "" : m.group(2).trim();
                int directiveColumn = comment.start() + m.start() + 1;
                switch (kind) {
                    case "suppress-all" -> {
                        if (!args.isBlank()) {
                            problems.add(problem(relativeFile, lineNo, directiveColumn,
                                    "lint:suppress-all takes no arguments", m.group()));
                        }
                        suppressAll.add(lineNo);
                        if (!codeBeforeMarker && lineNo + 1 <= lines.length) {
                            suppressAll.add(lineNo + 1);
                        }
                    }
                    case "suppress" -> {
                        List<RuleId> refs = parseRefs(relativeFile, lineNo, directiveColumn,
                                args, m.group(), knownRules, problems);
                        if (refs.isEmpty()) {
                            if (args.isBlank()) {
                                problems.add(problem(relativeFile, lineNo, directiveColumn,
                                        "lint:suppress requires at least one '<plugin>:<rule>'",
                                        m.group()));
                            }
                        } else {
                            addSingle(single, lineNo, refs);
                            if (!codeBeforeMarker && lineNo + 1 <= lines.length) {
                                addSingle(single, lineNo + 1, refs);
                            }
                        }
                    }
                    case "disable-all" -> {
                        if (openAll != null || openRules != null) {
                            problems.add(problem(relativeFile, lineNo, directiveColumn,
                                    "lint:disable-all nested inside another open lint:disable region",
                                    m.group()));
                            break;
                        }
                        if (!args.isBlank()) {
                            problems.add(problem(relativeFile, lineNo, directiveColumn,
                                    "lint:disable-all takes no arguments", m.group()));
                        }
                        openAll = new HashSet<>();
                        openStart = lineNo;
                    }
                    case "disable" -> {
                        if (openAll != null || openRules != null) {
                            problems.add(problem(relativeFile, lineNo, directiveColumn,
                                    "lint:disable nested inside another open lint:disable region",
                                    m.group()));
                            break;
                        }
                        List<RuleId> refs = parseRefs(relativeFile, lineNo, directiveColumn,
                                args, m.group(), knownRules, problems);
                        if (refs.isEmpty()) {
                            openAll = new HashSet<>();
                        } else {
                            openRules = new HashSet<>(refs);
                        }
                        openStart = lineNo;
                    }
                    case "enable" -> {
                        List<RuleId> refs = parseRefs(relativeFile, lineNo, directiveColumn,
                                args, m.group(), knownRules, problems);
                        int endBefore = lineNo; // diagnostics on the enable line are live again
                        if (openAll != null) {
                            regions.add(new Region(openStart, endBefore, true, Set.of()));
                        } else if (openRules != null) {
                            Set<RuleId> scope = refs.isEmpty() ? Set.copyOf(openRules)
                                    : intersect(openRules, refs);
                            regions.add(new Region(openStart, endBefore, false, scope));
                        }
                        openAll = null;
                        openRules = null;
                        openStart = -1;
                    }
                    default -> { }
                }
            }
        }
        if (openAll != null) {
            regions.add(new Region(openStart, lines.length, true, Set.of()));
        } else if (openRules != null) {
            regions.add(new Region(openStart, lines.length, false, Set.copyOf(openRules)));
        }
        return new ParsedSuppression(single, suppressAll, List.copyOf(regions), List.copyOf(problems));
    }

    private static void addSingle(Map<Integer, Set<RuleId>> single, int line, List<RuleId> refs) {
        single.computeIfAbsent(line, k -> new HashSet<>()).addAll(refs);
    }

    private static Set<RuleId> intersect(Set<RuleId> open, List<RuleId> enableRefs) {
        Set<RuleId> out = new HashSet<>(open);
        out.retainAll(enableRefs);
        return Set.copyOf(out);
    }

    private static List<RuleId> parseRefs(Path file, int lineNo, int column, String args,
                                          String raw, Set<RuleId> knownRules,
                                          List<Diagnostic> problems) {
        List<RuleId> refs = new ArrayList<>();
        if (args.isBlank()) {
            return refs;
        }
        for (String token : args.split("[\\s,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int colon = token.indexOf(':');
            if (colon <= 0 || colon == token.length() - 1 || token.indexOf(':', colon + 1) >= 0) {
                problems.add(problem(file, lineNo, column,
                        "malformed rule reference '" + token + "', expected '<plugin>:<rule>'", raw));
                continue;
            }
            RuleId id;
            try {
                id = RuleId.of(token.substring(0, colon), token.substring(colon + 1));
            } catch (IllegalArgumentException e) {
                problems.add(problem(file, lineNo, column,
                        "malformed rule reference '" + token + "'", raw));
                continue;
            }
            if (!knownRules.contains(id)) {
                problems.add(problem(file, lineNo, column,
                        "suppression references unknown rule " + id, raw));
                continue;
            }
            refs.add(id);
        }
        return refs;
    }

    private static Diagnostic problem(Path file, int line, String message, String raw) {
        return problem(file, line, 1, message, raw);
    }

    private static Diagnostic problem(Path file, int line, int column, String message, String raw) {
        return new Diagnostic(MARKER_RULE, file, line, column, Severity.WARNING,
                message + " [" + raw.strip() + "]", (Fix) null);
    }

    private record Region(int startLine, int endLineExclusive, boolean allRules, Set<RuleId> rules) {
        boolean covers(RuleId id, int line) {
            if (line < startLine || line >= endLineExclusive) {
                return false;
            }
            return allRules || rules.contains(id);
        }
    }

    private record ParsedSuppression(
            Map<Integer, Set<RuleId>> single,
            Set<Integer> suppressAllLines,
            List<Region> regions,
            List<Diagnostic> markerProblems
    ) implements SuppressionSet {

        @Override
        public boolean isSuppressed(RuleId ruleId, int line) {
            if (suppressAllLines.contains(line)) {
                return true;
            }
            Set<RuleId> rules = single.get(line);
            if (rules != null && rules.contains(ruleId)) {
                return true;
            }
            for (Region r : regions) {
                if (r.covers(ruleId, line)) {
                    return true;
                }
            }
            return false;
        }
    }
}
