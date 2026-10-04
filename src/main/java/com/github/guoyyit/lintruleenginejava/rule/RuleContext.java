package com.github.guoyyit.lintruleenginejava.rule;

import com.github.guoyyit.lintruleenginejava.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.model.Fix;
import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

import java.nio.file.Path;

/**
 * Context handed to a rule while checking a single file.
 * Rules report findings through {@link #report}.
 */
public interface RuleContext {

    /** File path relative to the analysis root. */
    Path relativePath();

    /** Absolute file path. */
    Path absolutePath();

    /** Full file content as UTF-8 text. */
    String content();

    /** Whether the given rule has been inline-suppressed at the given 1-based line. */
    boolean isSuppressed(RuleId ruleId, int line);

    /** Report a finding. The effective severity may be overridden by configuration. */
    void report(RuleId ruleId, int line, int column, Severity severity, String message, Fix fix);

    default void report(RuleId ruleId, int line, int column, Severity severity, String message) {
        report(ruleId, line, column, severity, message, null);
    }
}
