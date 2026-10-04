package com.github.guoyyit.lintruleenginejava.lint.engine;

import com.github.guoyyit.lintruleenginejava.lint.config.LintConfig;
import com.github.guoyyit.lintruleenginejava.lint.config.RuleConfig;
import com.github.guoyyit.lintruleenginejava.lint.model.SourceFile;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bounded, thread-safe incremental cache.
 *
 * <p>The cache key covers every input that can influence the result:
 * the file path, the file content, the effective config and the rule set
 * (rule ids, plugin ids and versions) and the engine version. Any change
 * invalidates the entry. The cache is bounded with LRU eviction so
 * long-running processes cannot grow memory without limit.
 */
public final class IncrementalCache {

    static final String ENGINE_VERSION = "1.0";

    private final int maxSize;
    private final Map<Object, FileCheckResult> entries;

    public IncrementalCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.entries = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, FileCheckResult> eldest) {
                return size() > IncrementalCache.this.maxSize;
            }
        };
    }

    /** Computes the cache key for a file given the current config and rule set. */
    public static Object keyFor(SourceFile file, LintConfig config, PluginRegistry registry) {
        StringBuilder sb = new StringBuilder();
        sb.append("engine=").append(ENGINE_VERSION).append('\n');
        sb.append("path=").append(file.path()).append('\n');
        sb.append("content=").append(sha256(file.content())).append('\n');
        sb.append("config=").append(fingerprintConfig(config)).append('\n');
        sb.append("rules=").append(fingerprintRules(registry));
        return sha256(sb.toString());
    }

    private static String fingerprintConfig(LintConfig config) {
        TreeMap<String, RuleConfig> sorted = new TreeMap<>(config.rules());
        StringBuilder sb = new StringBuilder();
        sorted.forEach((id, rc) -> sb.append(id).append('=')
                .append(rc.enabled()).append(':').append(rc.severity()).append(';'));
        sb.append("include=").append(String.join(",", config.include()));
        sb.append("exclude=").append(String.join(",", config.exclude()));
        return sha256(sb.toString());
    }

    private static String fingerprintRules(PluginRegistry registry) {
        List<String> parts = new ArrayList<>();
        for (LintRulePlugin plugin : registry.getPlugins()) {
            parts.add(plugin.getId() + '@' + plugin.getVersion());
            for (LintRule rule : plugin.getRules()) {
                parts.add("  " + rule.getId() + ':' + rule.getName() + ':'
                        + rule.getDefaultSeverity() + ':' + rule.isFixable() + ':'
                        + String.join("|", rule.getApplicableExtensions()));
            }
        }
        parts.sort(String::compareTo);
        return sha256(String.join("\n", parts));
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public synchronized FileCheckResult get(Object key) {
        return entries.get(key);
    }

    public synchronized void put(Object key, FileCheckResult result) {
        entries.put(key, result);
    }

    public synchronized int size() {
        return entries.size();
    }
}
