package com.github.guoyyit.lintruleenginejava.cache;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;

import java.util.List;

/** Cached analysis outcome for one file. */
public record FileResult(List<Diagnostic> diagnostics) {
}
