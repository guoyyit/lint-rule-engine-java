package com.github.guoyyit.lintruleenginejava.lint.fix;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.model.TextEdit;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of applying fixes to a single file.
 *
 * @param file                 the fixed file
 * @param status               APPLIED, ABORTED (content changed since check), or NO_FIXES
 * @param appliedEdits          edits that were applied
 * @param conflictingEdits     edits dropped because they conflicted with another edit
 * @param remainingDiagnostics diagnostics still present after re-checking the fixed content
 */
public record FixResult(Path file,
                        Status status,
                        List<TextEdit> appliedEdits,
                        List<TextEdit> conflictingEdits,
                        List<Diagnostic> remainingDiagnostics) {

    public enum Status { APPLIED, ABORTED_CONTENT_CHANGED, NO_FIXES, FAILED }

    public static FixResult noFixes(Path file) {
        return new FixResult(file, Status.NO_FIXES, List.of(), List.of(), List.of());
    }
}
