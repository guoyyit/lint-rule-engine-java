package com.github.guoyyit.lintruleenginejava.lint.spring;

import com.github.guoyyit.lintruleenginejava.lint.engine.LintEngine;
import com.github.guoyyit.lintruleenginejava.lint.plugin.LintRulePlugin;
import com.github.guoyyit.lintruleenginejava.lint.plugin.PluginRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Spring auto-configuration for the lint engine.
 *
 * <p>Plugins declared as Spring beans are picked up automatically; plugins
 * registered via {@link java.util.ServiceLoader} are added when
 * {@code lint.service-loader-enabled} is true (the default).
 */
@Configuration
@EnableConfigurationProperties(LintProperties.class)
public class LintAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PluginRegistry pluginRegistry(LintProperties properties,
                                         List<LintRulePlugin> springPlugins) {
        PluginRegistry base = properties.isServiceLoaderEnabled()
                ? PluginRegistry.load()
                : PluginRegistry.empty();
        return PluginRegistry.combine(base, springPlugins);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public LintEngine lintEngine(PluginRegistry registry, LintProperties properties) {
        return LintEngine.builder(registry)
                .root(properties.getRoot())
                .cacheSize(properties.getCacheSize())
                .threads(properties.getThreads())
                .perFileTimeoutMillis(properties.getPerFileTimeoutMillis())
                .build();
    }
}
