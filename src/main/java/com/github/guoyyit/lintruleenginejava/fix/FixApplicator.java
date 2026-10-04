package com.github.guoyyit.lintruleenginejava.fix;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies non-overlapping, deterministic auto-fixes to file content.
 *
 * <p>Procedure:
 * <ol>
 *   <li>collect every diagnostic carrying a fix, dropping exact duplicates;</li>
 *   <li>group fixes whose character ranges transitively overlap into clusters;</li>
 *   <li>within a cluster apply the {@link FixPolicy} to choose exactly one
 *       winner; every loser produces a {@link FixResult.FixConflict};</li>
 *   <li>apply the winners from right to left so offsets stay valid.</li>
 * </ol>
 * The transformation is pure: the original content is never half-written,
 * and identical inputs always produce identical outputs.
 */
public final class FixApplicator {

    private final FixPolicy policy;

    public FixApplicator(FixPolicy policy) {
        this.policy = policy == null ? FixPolicy.HIGHEST_RULE_ID : policy;
    }

    public FixResult apply(Path file, String originalContent, List<Diagnostic> diagnostics) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        String content = originalContent == null ? "" : originalContent;
        List<FixResult.AppliedFix> applied = new ArrayList<>();
        List<FixResult.FixConflict> conflicts = new ArrayList<>();

        List<Entry> entries = collect(content, diagnostics, conflicts);

        // Deterministic order before clustering: position, then rule id.
        entries.sort(Comparator
                .comparingInt((Entry e) -> e.fix().startOffset())
                .thenComparingInt(e -> e.fix().endOffset())
                .thenComparing(e -> e.ruleId().toString()));

        List<Fix> winners = new ArrayList<>();
        int i = 0;
        while (i < entries.size()) {
            int j = i;
            int clusterEnd = entries.get(i).fix().endOffset();
            while (j + 1 < entries.size()
                    && entries.get(j + 1).fix().startOffset() < clusterEnd) {
                j++;
                clusterEnd = Math.max(clusterEnd, entries.get(j).fix().endOffset());
            }
            resolveCluster(entries.subList(i, j + 1), winners, applied, conflicts);
            i = j + 1;
        }

        if (winners.isEmpty()) {
            return new FixResult(file, content, List.of(), List.copyOf(conflicts), false);
        }
        String newContent = splice(content, winners);
        applied.sort(Comparator
                .comparing((FixResult.AppliedFix a) -> a.fix().startOffset())
                .thenComparing(a -> a.ruleId().toString()));
        return new FixResult(file, newContent, List.copyOf(applied),
                List.copyOf(conflicts), !newContent.equals(content));
    }

    private record Entry(RuleId ruleId, Fix fix) {
    }

    private List<Entry> collect(String content, List<Diagnostic> diagnostics,
                                List<FixResult.FixConflict> conflicts) {
        Set<Key> seen = new HashSet<>();
        List<Entry> entries = new ArrayList<>();
        if (diagnostics == null) {
            return entries;
        }
        for (Diagnostic d : diagnostics) {
            Fix fix = d.fix();
            if (fix == null) {
                continue;
            }
            if (fix.endOffset() > content.length()) {
                conflicts.add(new FixResult.FixConflict(null, null,
                        List.of(d.ruleId()),
                        "fix range ends beyond file length ("
                                + fix.endOffset() + " > " + content.length() + ")"));
                continue;
            }
            Key key = new Key(d.ruleId(), fix);
            if (seen.add(key)) {
                entries.add(new Entry(d.ruleId(), fix));
            }
        }
        return entries;
    }

    private void resolveCluster(List<Entry> cluster, List<Fix> winners,
                                List<FixResult.AppliedFix> applied,
                                List<FixResult.FixConflict> conflicts) {
        if (cluster.size() == 1) {
            Entry only = cluster.get(0);
            winners.add(only.fix());
            applied.add(new FixResult.AppliedFix(only.ruleId(), only.fix()));
            return;
        }
        // Policy HIGHEST_RULE_ID: the greatest plugin:rule id wins; if the
        // same rule produced several overlapping fixes, the smallest
        // (start, end, replacement) wins. Fully deterministic either way.
        String topRule = cluster.stream()
                .map(e -> e.ruleId().toString())
                .max(Comparator.naturalOrder())
                .orElseThrow();
        Entry winner = cluster.stream()
                .filter(e -> e.ruleId().toString().equals(topRule))
                .min(Comparator
                        .comparingInt((Entry e) -> e.fix().startOffset())
                        .thenComparingInt(e -> e.fix().endOffset())
                        .thenComparing(e -> e.fix().replacement()))
                .orElseThrow();
        winners.add(winner.fix());
        applied.add(new FixResult.AppliedFix(winner.ruleId(), winner.fix()));
        List<RuleId> rejected = new ArrayList<>();
        for (Entry e : cluster) {
            if (!e.equals(winner)) {
                rejected.add(e.ruleId());
            }
        }
        rejected.sort(Comparator.comparing(RuleId::toString));
        conflicts.add(new FixResult.FixConflict(winner.fix(), winner.ruleId(),
                List.copyOf(rejected),
                "overlapping fixes at offsets " + winner.fix().startOffset()
                        + "-" + winner.fix().endOffset()
                        + "; policy " + policy + " selected " + winner.ruleId()));
    }

    private static String splice(String content, List<Fix> fixes) {
        List<Fix> sorted = fixes.stream()
                .sorted(Comparator.comparingInt(Fix::startOffset)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(Fix::endOffset).reversed()))
                .toList();
        StringBuilder sb = new StringBuilder(content);
        for (Fix fix : sorted) {
            sb.replace(fix.startOffset(), fix.endOffset(), fix.replacement());
        }
        return sb.toString();
    }

    private record Key(RuleId ruleId, Fix fix) {
    }
}
