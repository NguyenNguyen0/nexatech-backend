package com.nexatech.user.config.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.config.client.ConfigClientProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Logs a clear warning at startup when the remote config-server could not be reached
 * and the service is running with local/default values only.
 *
 * <p>This makes it immediately obvious in logs why a service might behave differently
 * from expected (e.g. wrong Keycloak issuer-uri, wrong DB URL) without having to dig
 * through Spring internals.</p>
 */
@Slf4j
@Component
public class ConfigServerStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private static final List<String> WATCHED_KEYS = List.of(
            "spring.security.oauth2.resourceserver.jwt.issuer-uri",
            "spring.datasource.url",
            "spring.data.redis.host",
            "spring.kafka.bootstrap-servers",
            "openfeign.identity-service.url"
    );

    private final ConfigurableEnvironment environment;
    private final ConfigClientProperties configClientProperties;

    public ConfigServerStartupLogger(ConfigurableEnvironment environment,
                                     ConfigClientProperties configClientProperties) {
        this.environment = environment;
        this.configClientProperties = configClientProperties;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        boolean configServerLoaded = environment.getPropertySources().stream()
                .anyMatch(ps -> ps.getName().contains("configserver")
                        || ps.getName().contains("Config Server"));

        if (!configServerLoaded) {
            log.warn("══════════════════════════════════════════════════════════════");
            log.warn("  CONFIG SERVER NOT LOADED — running with local/default values");
            log.warn("  Config server URI: {}", configClientProperties.getUri());
            log.warn("  Set CONFIG_SERVER_URI env var or start config-server first.");
            log.warn("  Active property sources: {}",
                    environment.getPropertySources().stream()
                            .map(PropertySource::getName)
                            .toList());
            log.warn("══════════════════════════════════════════════════════════════");
        } else {
            log.info("Config server loaded successfully from: {}",
                    configClientProperties.getUri());
        }

        log.info("── Effective configuration snapshot ──────────────────────────");
        for (String key : WATCHED_KEYS) {
            String value = environment.getProperty(key, "(not set)");
            // Mask sensitive parts of URLs (passwords)
            String masked = maskSensitive(key, value);
            String source = findSource(key);
            log.info("  {} = {} [from: {}]", key, masked, source);
        }
        log.info("─────────────────────────────────────────────────────────────");
    }

    private String maskSensitive(String key, String value) {
        if (value == null || value.equals("(not set)")) return value;
        // Mask passwords in JDBC URLs: jdbc:postgresql://host/db?password=secret
        if (key.contains("datasource.url")) {
            return value.replaceAll("(?i)(password=)[^&;]+", "$1***");
        }
        return value;
    }

    private String findSource(String key) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> eps) {
                if (Arrays.asList(eps.getPropertyNames()).contains(key)) {
                    return source.getName();
                }
            }
        }
        return "default";
    }
}
