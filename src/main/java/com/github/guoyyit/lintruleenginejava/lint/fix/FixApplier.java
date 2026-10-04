package com.github.guoyyit.lintruleenginejava.lint.fix;

import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.Position;
import com.github.guoyyit.lintruleenginejava.lint.model.TextEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies auto-fixes deterministically and safely:
 * <ul>
 *   <li>fixes are only applied if the file content still matches the content
 *       the diagnostics were computed from (no overwriting of user changes);</li>
 *   <li>overlapping edits are resolved by a fixed, deterministic priority
 *       (earlier start, then earlier end, then rule id) — the losing edit is
 *       reported as conflicting;</li>
 *   <li>files are written atomically, never leaving half-written state;</li>
 *   <li>after applying, the file is re-checked to confirm the issues are gone.</li>
 * </ul>
 */
public final class FixApplier {

    private final LintEngine engine;

    public FixApplier(LintEngine engine) {
        this.engine = engine;
    }

    /**
     * Fixes the given diagnostics on a file.
     *
     * @param file        the file to fix
     * @param diagnostics fixable diagnostics previously produced for the file
     * @param baseContent the content the diagnostics were computed from
     */
    public FixResult fix(Path file, List<Diagnostic> diagnostics, String baseContent) {
        String currentContent;
        try {
            currentContent = Files.readString(file);
        } catch (IOException e) {
            return new FixResult(file, FixResult.Status.FAILED, List.of(), List.of(), List.of());
        }
        if (!currentContent.equals(baseContent)) {
            return new FixResult(file, FixResult.Status.ABORTED_CONTENT_CHANGED,
                    List.of(), List.of(), List.of());
        }

        List<Diagnostic> fixable = diagnostics.stream()
                .filter(d -> d.fixOpt().isPresent())
                .toList();
        if (fixable.isEmpty()) {
            return FixResult.noFixes(file);
        }

        List<OwnedEdit> ownedEdits = new ArrayList<>();
        for (Diagnostic d : fixable) {
            for (TextEdit edit : d.fix().edits()) {
                ownedEdits.add(new OwnedEdit(edit, d.ruleId()));
            }
        }
        ownedEdits.sort(Comparator
                .comparingInt((OwnedEdit o) -> offset(currentContent, o.edit.range().start()))
                .thenComparingInt(o -> offset(currentContent, o.edit.range().end()))
                .thenComparing(o -> o.ruleId));

        List<TextEdit> applied = new ArrayList<>();
        List<TextEdit> conflicting = new ArrayList<>();
        List<int[]> keptRanges = new ArrayList<>();
        for (OwnedEdit owned : ownedEdits) {
            TextEdit edit = owned.edit;
            int start = offset(currentContent, edit.range().start());
            int end = offset(currentContent, edit.range().end());
            if (start < 0 || end < start || end > currentContent.length()) {
                conflicting.add(edit);
                continue;
            }
            boolean overlaps = false;
            for (int[] range : keptRanges) {
                if (start < range[1] && range[0] < end) {
                    overlaps = true;
                    break;
                }
            }
            if (overlaps) {
                conflicting.add(edit);
            } else {
                applied.add(edit);
                keptRanges.add(new int[]{start, end});
            }
        }

        if (applied.isEmpty()) {
            return new FixResult(file, FixResult.Status.NO_FIXES, List.of(), conflicting, List.of());
        }

        String newContent;
        try {
            newContent = applyEdits(currentContent, applied);
            atomicWrite(file, newContent);
        } catch (IOException e) {
            return new FixResult(file, FixResult.Status.FAILED, applied, conflicting, List.of());
        }

        FileCheckResult recheck = engine.checkOne(file);
        List<Diagnostic> remaining = recheck.status() == FileCheckResult.Status.OK
                ? recheck.diagnostics() : List.of();
        return new FixResult(file, FixResult.Status.APPLIED, applied, conflicting, remaining);
    }

    /** Applies a set of edits to content. Edits must not overlap. */
    public static String applyEdits(String content, List<TextEdit> edits) {
        List<TextEdit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator
                .comparingInt((TextEdit e) -> offset(content, e.range().start()))
                .thenComparingInt(e -> offset(content, e.range().end())));
        StringBuilder sb = new StringBuilder(content);
        for (int i = sorted.size() - 1; i >= 0; i--) {
            TextEdit edit = sorted.get(i);
            int start = offset(content, edit.range().start());
            int end = offset(content, edit.range().end());
            sb.replace(start, end, edit.newText());
        }
        return sb.toString();
    }

    private record OwnedEdit(TextEdit edit, String ruleId) {
    }

    private static int offset(String content, Position pos) {
        int line = Math.max(1, pos.line());
        int column = Math.max(1, pos.column());
        int currentLine = 1;
        int lineStart = 0;
        for (int i = 0; i < content.length(); i++) {
            if (currentLine == line) {
                break;
            }
            if (content.charAt(i) == '\n') {
                currentLine++;
                lineStart = i + 1;
            }
        }
        if (currentLine < line) {
            return content.length();
        }
        return Math.min(lineStart + column - 1, content.length());
    }

    private static void atomicWrite(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Path temp = Files.createTempFile(parent, ".lint-fix-", ".tmp");
        try {
            Files.writeString(temp, content);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort cleanup
            }
            throw e;
        }
    }
}
