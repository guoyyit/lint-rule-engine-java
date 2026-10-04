package com.github.guoyyit.lintruleenginejava.lint.spring;

import com.github.guoyyit.lintruleenginejava.lint.engine.CheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.FileCheckResult;
import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.model.Diagnostic;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRule;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginLoadFailure;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for inspecting plugins and running checks.
 */
@RestController
@RequestMapping("/api/lint")
public class LintController {

    private final LintEngine engine;
    private final PluginRegistry registry;

    public LintController(LintEngine engine, PluginRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    @GetMapping("/plugins")
    public Map<String, Object> plugins() {
        List<Map<String, Object>> pluginList = registry.getPlugins().stream().map(p -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("version", p.getVersion());
            map.put("rules", p.getRules().stream().map(LintRule::getId).toList());
            return map;
        }).toList();
        List<Map<String, Object>> failureList = registry.getFailures().stream().map(f -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("plugin", f.pluginId());
            map.put("reason", f.message());
            return map;
        }).toList();
        return Map.of("plugins", pluginList, "failures", failureList);
    }

    @PostMapping("/check")
    public Map<String, Object> check(@RequestBody(required = false) Map<String, Object> request) {
        Map<String, Object> req = request != null ? request : Map.of();
        CheckResult result;
        try {
            if (req.containsKey("files")) {
                @SuppressWarnings("unchecked")
                List<String> files = (List<String>) req.get("files");
                result = engine.check(files.stream().map(Path::of).toList());
            } else {
                result = engine.checkDirectory();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return toResponse(result);
    }

    private static Map<String, Object> toResponse(CheckResult result) {
        List<Map<String, Object>> diagnostics = result.diagnostics().stream()
                .map(LintController::toMap)
                .toList();
        List<Map<String, Object>> files = result.fileResults().stream()
                .map(LintController::toMap)
                .toList();
        return Map.of(
                "diagnostics", diagnostics,
                "files", files,
                "pluginFailures", result.pluginFailures().stream().map(LintController::toMap).toList(),
                "checkedFiles", result.checkedFiles(),
                "cacheHits", result.cacheHits());
    }

    private static Map<String, Object> toMap(Diagnostic d) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category", d.category().name());
        map.put("ruleId", d.ruleId());
        map.put("severity", d.severity().name());
        map.put("file", d.filePath().toString());
        map.put("line", d.range().start().line());
        map.put("column", d.range().start().column());
        map.put("message", d.message());
        map.put("fixable", d.fixOpt().isPresent());
        return map;
    }

    private static Map<String, Object> toMap(FileCheckResult r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("file", r.file().toString());
        map.put("status", r.status().name());
        map.put("fromCache", r.fromCache());
        map.put("error", r.errorMessage());
        map.put("diagnosticCount", r.diagnostics().size());
        return map;
    }

    private static Map<String, Object> toMap(PluginLoadFailure f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("plugin", f.pluginId());
        map.put("reason", f.message());
        return map;
    }
}
