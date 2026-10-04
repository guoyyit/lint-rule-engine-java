package com.github.guoyyit.lintruleenginejava.config;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;

import java.util.List;
import java.util.Map;

/**
 * Result of parsing one raw config file.
 */
public record ParsedConfig(
        Map<RuleId, Severity> severityOverrides,
        List<RuleId> disabledRules,
        List<String> globs,
        List<ConfigProblem> problems,
        String contentHash
) {
}
