package com.github.guoyyit.lintruleenginejava.lint.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.lintruleenginejava.lint.model.Severity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Loads {@link LintConfig} from {@code .lintrc.json} files.
 *
 * <p>File format:
 * <pre>{@code
 * {
 *   "rules": {
 *     "rule-id": { "enabled": true, "severity": "error" }
 *   },
 *   "include": ["glob"],
 *   "exclude": ["glob"]
 * }
 * }</pre>
 */
public final class ConfigLoader {

    public static final String CONFIG_FILE_NAME = ".lintrc.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigLoader() {
    }

    /**
     * Loads the config file in the given directory, if present.
     * A malformed config is reported via an {@link IOException} carrying the file path.
     */
    public static Optional<LintConfig> loadInDirectory(Path directory) throws IOException {
        Path configFile = directory.resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(Files.readString(configFile));
            Map<String, RuleConfig> rules = new LinkedHashMap<>();
            if (root.has("rules") && root.get("rules").isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = root.get("rules").fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> entry = fields.next();
                    rules.put(entry.getKey(), parseRuleConfig(entry.getValue(), configFile));
                }
            }
            Set<String> include = readStringSet(root, "include");
            Set<String> exclude = readStringSet(root, "exclude");
            return Optional.of(new LintConfig(rules, include, exclude));
        } catch (IOException e) {
            throw new IOException("Failed to parse lint config " + configFile + ": " + e.getMessage(), e);
        }
    }

    private static RuleConfig parseRuleConfig(JsonNode node, Path configFile) throws IOException {
        if (node == null || node.isNull()) {
            return new RuleConfig(null, null);
        }
        if (node.isBoolean()) {
            return new RuleConfig(node.booleanValue(), null);
        }
        Boolean enabled = node.has("enabled") && !node.get("enabled").isNull()
                ? node.get("enabled").booleanValue() : null;
        Severity severity = null;
        if (node.has("severity") && !node.get("severity").isNull()) {
            String raw = node.get("severity").asText().toUpperCase();
            try {
                severity = Severity.valueOf(raw);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unknown severity '" + raw + "' in " + configFile
                        + " (expected one of error, warning, info)");
            }
        }
        return new RuleConfig(enabled, severity);
    }

    private static Set<String> readStringSet(JsonNode root, String fieldName) {
        if (!root.has(fieldName) || !root.get(fieldName).isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        root.get(fieldName).forEach(node -> result.add(node.asText()));
        return result;
    }
}
