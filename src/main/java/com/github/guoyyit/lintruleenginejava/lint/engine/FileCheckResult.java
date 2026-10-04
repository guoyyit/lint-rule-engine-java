package com.github.guoyyit.lintruleenginejava.lint.engine;

import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;

import java.nio.file.Path;
import java.util.List;

/**
 * The outcome of checking a single file.
 *
 * @param file         the checked file
 * @param diagnostics  diagnostics after suppression (lint diagnostics + suppression hints)
 * @param status       how the check finished
 * @param errorMessage failure reason when status != OK
 * @param fromCache    whether the result was served from the incremental cache
 */
public record FileCheckResult(Path file,
                              List<Diagnostic> diagnostics,
                              Status status,
                              String errorMessage,
                              boolean fromCache) {

    public enum Status { OK, FAILED, TIMED_OUT, CANCELLED }

    public static FileCheckResult ok(Path file, List<Diagnostic> diagnostics) {
        return new FileCheckResult(file, List.copyOf(diagnostics), Status.OK, null, false);
    }

    public static FileCheckResult ok(Path file, List<Diagnostic> diagnostics, boolean fromCache) {
        return new FileCheckResult(file, List.copyOf(diagnostics), Status.OK, null, fromCache);
    }

    public static FileCheckResult failure(Path file, Status status, String message) {
        return new FileCheckResult(file, List.of(), status, message, false);
    }

    /** Returns a copy marked as served from the cache. */
    public FileCheckResult asCacheHit() {
        return new FileCheckResult(file, diagnostics, status, errorMessage, true);
    }
}
