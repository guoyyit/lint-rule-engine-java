package com.github.guoyyit.lintruleenginejava.plugin;

import com.github.guoyyit.lintruleenginejava.model.RuleId;
import com.github.guoyyit.lintruleenginejava.rule.Rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Loads plugins from one or more {@link PluginSource}s, isolating every
 * failure so that one broken plugin never prevents the others from loading.
 * Results are ordered deterministically by rule id.
 */
public final class PluginLoader {

    /** Load from the given sources; never throws for individual plugin failures. */
    public PluginRegistry load(List<PluginSource> sources) {
        List<PluginSource> safe = sources == null ? List.of() : List.copyOf(sources);
        // Collect candidates first so failures are reported in source order.
        List<Candidate> candidates = new ArrayList<>();
        List<PluginLoadFailure> failures = new ArrayList<>();
        for (PluginSource source : safe) {
            if (source instanceof PluginSource.ClassNames cn) {
                loadClassNames(cn, candidates, failures);
            } else if (source instanceof PluginSource.ServiceLoader sl) {
                loadServiceLoader(sl, candidates, failures);
            }
        }
        Map<RuleId, Rule> byId = new LinkedHashMap<>();
        Set<String> pluginNames = new HashSet<>();
        for (Candidate c : candidates) {
            LintPlugin plugin = instantiate(c, failures);
            if (plugin == null) {
                continue;
            }
            register(c, plugin, byId, pluginNames, failures);
        }
        List<String> sortedNames = pluginNames.stream().sorted().toList();
        return new DefaultPluginRegistry(byId, failures, sortedNames);
    }

    private void loadClassNames(PluginSource.ClassNames cn, List<Candidate> out,
                                List<PluginLoadFailure> failures) {
        int idx = 0;
        for (String className : cn.classNames()) {
            String source = "class:" + className + "#" + idx;
            Class<?> clazz;
            try {
                clazz = Class.forName(className, true, cn.classLoader());
            } catch (Throwable t) {
                failures.add(failure(null, source,
                        "plugin class could not be loaded: " + t.getMessage(), t));
                idx++;
                continue;
            }
            if (!LintPlugin.class.isAssignableFrom(clazz)) {
                failures.add(failure(null, source,
                        className + " does not implement LintPlugin", null));
                idx++;
                continue;
            }
            out.add(new Candidate(source, clazz.asSubclass(LintPlugin.class)));
            idx++;
        }
    }

    private void loadServiceLoader(PluginSource.ServiceLoader sl, List<Candidate> out,
                                   List<PluginLoadFailure> failures) {
        String location = "serviceloader" + (sl.location() != null ? ":" + sl.location() : "");
        ServiceLoader<LintPlugin> loader = ServiceLoader.load(LintPlugin.class, sl.classLoader());
        Iterator<LintPlugin> it;
        try {
            it = loader.iterator();
        } catch (Throwable t) {
            failures.add(failure(null, location,
                    "service lookup failed: " + t.getMessage(), t));
            return;
        }
        while (true) {
            LintPlugin plugin;
            String source;
            try {
                if (!it.hasNext()) {
                    break;
                }
                plugin = it.next();
                source = location;
            } catch (ServiceConfigurationError | RuntimeException | LinkageError t) {
                failures.add(failure(null, location,
                        "plugin service configuration failed: " + t.getMessage(), t));
                // Try to continue with the next provider; the iterator may still be usable.
                continue;
            }
            if (plugin == null) {
                failures.add(failure(null, location, "service returned a null plugin", null));
                continue;
            }
            out.add(new Candidate(source, plugin));
        }
    }

    private LintPlugin instantiate(Candidate c, List<PluginLoadFailure> failures) {
        if (c.instance != null) {
            return c.instance;
        }
        try {
            return c.clazz.getDeclaredConstructor().newInstance();
        } catch (Throwable t) {
            failures.add(failure(null, c.source,
                    "plugin could not be instantiated: " + rootMessage(t), t));
            return null;
        }
    }

    private void register(Candidate c, LintPlugin plugin, Map<RuleId, Rule> byId,
                          Set<String> pluginNames, List<PluginLoadFailure> failures) {
        String name;
        try {
            name = plugin.name();
        } catch (Throwable t) {
            failures.add(failure(null, c.source,
                    "plugin.name() threw: " + rootMessage(t), t));
            return;
        }
        if (name == null || name.isBlank()) {
            failures.add(failure(null, c.source, "plugin declared a blank name", null));
            return;
        }
        List<Rule> rules;
        try {
            rules = plugin.rules();
        } catch (Throwable t) {
            failures.add(failure(new PluginDescriptor(name, c.source),
                    "plugin.rules() threw: " + rootMessage(t), t));
            return;
        }
        if (rules == null) {
            failures.add(failure(new PluginDescriptor(name, c.source),
                    "plugin.rules() returned null", null));
            return;
        }
        pluginNames.add(name.toLowerCase());
        for (Rule rule : rules) {
            if (rule == null) {
                failures.add(failure(new PluginDescriptor(name, c.source),
                        "plugin contributed a null rule entry", null));
                continue;
            }
            RuleId declared;
            try {
                declared = rule.id();
            } catch (Throwable t) {
                failures.add(failure(new PluginDescriptor(name, c.source),
                        "rule.id() threw: " + rootMessage(t), t));
                continue;
            }
            if (declared == null) {
                failures.add(failure(new PluginDescriptor(name, c.source),
                        "rule returned a null id", null));
                continue;
            }
            RuleId expected = RuleId.of(name, declared.rule());
            if (!declared.plugin().equals(expected.plugin())) {
                failures.add(failure(new PluginDescriptor(name, c.source),
                        "rule " + declared + " does not belong to plugin '"
                                + name.toLowerCase() + "'", null));
                continue;
            }
            if (byId.putIfAbsent(expected, rule) != null) {
                failures.add(failure(new PluginDescriptor(name, c.source),
                        "duplicate rule id " + expected + " ignored", null));
            }
        }
    }

    private static PluginLoadFailure failure(PluginDescriptor descriptor, String reason, Throwable t) {
        return failure(descriptor,
                descriptor == null ? "<unknown>" : descriptor.source(), reason, t);
    }

    private static PluginLoadFailure failure(PluginDescriptor descriptor, String source,
                                             String reason, Throwable t) {
        if (descriptor == null) {
            descriptor = new PluginDescriptor("<unknown>", source);
        }
        return new PluginLoadFailure(descriptor, reason,
                t == null ? null : rootCause(t).getClass().getName());
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = rootCause(t);
        return cur.getClass().getSimpleName()
                + (cur.getMessage() != null ? ": " + cur.getMessage() : "");
    }

    private record Candidate(String source, Class<? extends LintPlugin> clazz, LintPlugin instance) {
        Candidate(String source, Class<? extends LintPlugin> clazz) {
            this(source, clazz, null);
        }

        Candidate(String source, LintPlugin instance) {
            this(source, null, instance);
        }
    }
}
