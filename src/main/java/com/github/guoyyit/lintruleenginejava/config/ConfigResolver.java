package com.github.guoyyit.lintruleenginejava.config;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.model.Severity;
import com.github.guoyyit.lintruleenginejava.util.Hashes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the effective configuration for files by merging {@code .lintrc}
 * files from the analysis root down to the file's parent directory:
 * child entries inherit from, then override, parent entries.
 *
 * <p>File format (line based, {@code #} starts a comment):
 * <pre>
 * severity plugin:rule=error
 * disable plugin:rule
 * include **&#47;*.txt
 * </pre>
 * Invalid references are collected as {@link ConfigProblem}s rather than
 * aborting the run.
 */
public final class ConfigResolver {

    public static final String CONFIG_FILE_NAME = ".lintrc";
    private static final String KEY_SEVERITY = "severity";
    private static final String KEY_DISABLE = "disable";
    private static final String KEY_INCLUDE = "include";

    private final Path root;
    private final Map<Path, CachedLayer> layerCache = new ConcurrentHashMap<>();

    public ConfigResolver(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        this.root = root.toAbsolutePath().normalize();
    }

    /** All parse problems of the layers applicable to a file. */
    public List<ConfigProblem> problemsFor(Path relativeFile) {
        List<ConfigProblem> all = new ArrayList<>();
        for (Path dir : dirsOf(relativeFile)) {
            all.addAll(layer(dir).parsed.problems());
        }
        return all;
    }

    /** Effective configuration for the given relative file path. */
    public LintConfig resolve(Path relativeFile) {
        if (relativeFile == null) {
            return LintConfig.empty();
        }
        Path norm = relativeFile.normalize();
        if (norm.isAbsolute()) {
            norm = root.relativize(norm.toAbsolutePath().normalize());
        }
        Map<RuleId, Severity> sev = new HashMap<>();
        List<RuleId> disabled = new ArrayList<>();
        List<String> globs = new ArrayList<>();
        for (Path dir : dirsOf(norm)) {
            ParsedConfig layer = layer(dir).parsed;
            sev.putAll(layer.severityOverrides());
            for (RuleId id : layer.disabledRules()) {
                if (!disabled.contains(id)) {
                    disabled.add(id);
                }
            }
            globs.addAll(layer.globs());
        }
        return new LintConfig(sev, disabled, globs);
    }

    /**
     * Stable hash covering the content of every config layer that applies to
     * the file, so any change to any relevant {@code .lintrc} changes the key.
     */
    public String fingerprint(Path relativeFile) {
        StringBuilder sb = new StringBuilder("cfg:");
        for (Path dir : dirsOf(relativeFile == null ? Path.of("") : relativeFile.normalize())) {
            ParsedConfig layer = layer(dir).parsed;
            sb.append(layer.contentHash()).append(';');
        }
        return Hashes.sha256(sb.toString());
    }

    private List<Path> dirsOf(Path relativeFile) {
        Path fileDir = relativeFile == null ? Path.of("")
                : (relativeFile.getParent() == null ? Path.of("") : relativeFile.getParent());
        List<Path> dirs = new ArrayList<>();
        Path dir = Path.of("");
        dirs.add(dir);
        for (Path part : fileDir) {
            dir = dir.resolve(part);
            dirs.add(dir);
        }
        return dirs;
    }

    private CachedLayer layer(Path relativeDir) {
        Path file = root.resolve(relativeDir).resolve(CONFIG_FILE_NAME);
        boolean present = Files.isRegularFile(file);
        long mtime = 0L;
        if (present) {
            try {
                mtime = Files.getLastModifiedTime(file).toMillis();
            } catch (IOException ignored) {
                // handled below via parse
            }
        }
        CachedLayer cached = layerCache.get(relativeDir);
        if (cached != null && cached.present() == present
                && (!present || cached.lastModified() == mtime)) {
            return cached;
        }
        CachedLayer fresh;
        if (!present) {
            fresh = CachedLayer.missing();
        } else {
            try {
                fresh = new CachedLayer(mtime, parse(file), true);
            } catch (IOException e) {
                ParsedConfig broken = new ParsedConfig(
                        Map.of(), List.of(), List.of(),
                        List.of(new ConfigProblem(file, 1,
                                "unreadable config file: " + e.getClass().getSimpleName())),
                        "unreadable");
                fresh = new CachedLayer(mtime, broken, true);
            }
        }
        layerCache.put(relativeDir, fresh);
        return fresh;
    }

    /**
     * Parse one config file into a raw (unmerged) config layer.
     * Invalid references (malformed rule id / bad severity) are returned as
     * parse problems rather than thrown.
     */
    public static ParsedConfig parse(Path configFile) throws IOException {
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        String hash = Hashes.sha256(content);
        Map<RuleId, Severity> sev = new HashMap<>();
        List<RuleId> disabled = new ArrayList<>();
        List<String> globs = new ArrayList<>();
        List<ConfigProblem> problems = new ArrayList<>();

        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            int lineNo = i + 1;
            String raw = lines[i];
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            String key;
            String value;
            int sp = firstWhitespace(line);
            if (sp < 0) {
                key = line;
                value = "";
            } else {
                key = line.substring(0, sp);
                value = line.substring(sp + 1).trim();
            }
            switch (key) {
                case KEY_SEVERITY -> parseSeverity(configFile, lineNo, value, sev, problems);
                case KEY_DISABLE -> parseDisable(configFile, lineNo, value, disabled, problems);
                case KEY_INCLUDE -> parseInclude(configFile, lineNo, value, globs, problems);
                default -> problems.add(new ConfigProblem(configFile, lineNo,
                        "unknown config directive '" + key + "' (expected severity|disable|include)"));
            }
        }
        return new ParsedConfig(Map.copyOf(sev), List.copyOf(disabled),
                List.copyOf(globs), List.copyOf(problems), hash);
    }

    private static void parseSeverity(Path file, int lineNo, String value,
                                      Map<RuleId, Severity> out, List<ConfigProblem> problems) {
        int eq = value.lastIndexOf('=');
        if (eq <= 0 || eq == value.length() - 1) {
            problems.add(new ConfigProblem(file, lineNo,
                    "invalid severity entry '" + value + "', expected '<plugin>:<rule>=<info|warning|error>'"));
            return;
        }
        String ref = value.substring(0, eq).trim();
        String level = value.substring(eq + 1).trim().toUpperCase();
        RuleId id = parseRuleRef(file, lineNo, ref, problems);
        if (id == null) {
            return;
        }
        try {
            out.put(id, Severity.valueOf(level));
        } catch (IllegalArgumentException e) {
            problems.add(new ConfigProblem(file, lineNo,
                    "unknown severity '" + level + "' for rule " + id
                            + " (expected INFO, WARNING or ERROR)"));
        }
    }

    private static void parseDisable(Path file, int lineNo, String value,
                                     List<RuleId> out, List<ConfigProblem> problems) {
        if (value.isEmpty()) {
            problems.add(new ConfigProblem(file, lineNo,
                    "disable requires a rule reference '<plugin>:<rule>'"));
            return;
        }
        for (String part : value.split("[\\s,]+")) {
            if (part.isEmpty()) {
                continue;
            }
            RuleId id = parseRuleRef(file, lineNo, part, problems);
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
    }

    private static void parseInclude(Path file, int lineNo, String value,
                                     List<String> out, List<ConfigProblem> problems) {
        if (value.isEmpty()) {
            problems.add(new ConfigProblem(file, lineNo, "include requires a glob pattern"));
            return;
        }
        out.add(value);
    }

    private static RuleId parseRuleRef(Path file, int lineNo, String ref, List<ConfigProblem> problems) {
        int colon = ref.indexOf(':');
        if (colon <= 0 || colon == ref.length() - 1 || ref.indexOf(':', colon + 1) >= 0) {
            problems.add(new ConfigProblem(file, lineNo,
                    "invalid rule reference '" + ref + "', expected '<plugin>:<rule>'"));
            return null;
        }
        try {
            return RuleId.of(ref.substring(0, colon), ref.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            problems.add(new ConfigProblem(file, lineNo, "invalid rule reference '" + ref + "'"));
            return null;
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    private static int firstWhitespace(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private record CachedLayer(long lastModified, ParsedConfig parsed, boolean present) {
        static CachedLayer missing() {
            ParsedConfig empty = new ParsedConfig(
                    Collections.emptyMap(), List.of(), List.of(), List.of(),
                    Hashes.sha256(""));
            return new CachedLayer(0L, empty, false);
        }
    }
}
