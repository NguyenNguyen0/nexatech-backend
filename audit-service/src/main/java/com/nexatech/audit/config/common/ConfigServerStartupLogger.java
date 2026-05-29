package com.nexatech.audit.config.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ConfigServerStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final ConfigurableEnvironment environment;

    public ConfigServerStartupLogger(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("[STARTUP] Checking dependencies...");
        logConfigServerStatus();
        logEffectiveSnapshot(List.of(
                "spring.mongodb.uri",
                "spring.kafka.bootstrap-servers",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                "server.port",
                "management.endpoint.health.probes.enabled"
        ));

        checkMongo("MongoDB", "spring.mongodb.uri", 27017);
        checkBootstrapServer("Kafka", "spring.kafka.bootstrap-servers", 9092);
        checkUrl("Keycloak issuer", "spring.security.oauth2.resourceserver.jwt.issuer-uri", 9000);
    }

    private void logConfigServerStatus() {
        boolean configServerLoaded = environment.getPropertySources().stream()
                .anyMatch(ps -> ps.getName().contains("configserver") || ps.getName().contains("Config Server"));
        String configServerUri = environment.getProperty("CONFIG_SERVER_URI",
                environment.getProperty("spring.cloud.config.uri", "http://localhost:8888"));
        if (configServerLoaded) {
            log.info("[STARTUP] + Config server: loaded ({})", configServerUri);
        } else {
            log.warn("[STARTUP] - Config server: not loaded, running with local/default config ({})", configServerUri);
        }
    }

    private void logEffectiveSnapshot(List<String> keys) {
        for (String key : keys) {
            String value = environment.getProperty(key, "(not set)");
            String source = findSource(key);
            log.info("[STARTUP] {} = {} [from: {}]", key, maskSensitive(key, value), source);
        }
    }

    private void checkMongo(String label, String key, int defaultPort) {
        String uriString = environment.getProperty(key, "");
        if (uriString.isBlank()) {
            log.warn("[STARTUP] - {}: URI is missing", label);
            return;
        }
        try {
            String normalized = uriString.replace("mongodb://", "http://").replace("mongodb+srv://", "http://");
            URI uri = URI.create(normalized);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            if (host == null || host.isBlank()) {
                log.warn("[STARTUP] - {}: invalid URI ({})", label, uriString);
                return;
            }
            checkSocket(label, host, port);
        } catch (Exception ex) {
            log.warn("[STARTUP] - {}: invalid URI ({})", label, uriString);
        }
    }

    private void checkBootstrapServer(String label, String key, int defaultPort) {
        String bootstrap = environment.getProperty(key, "");
        if (bootstrap.isBlank()) {
            log.warn("[STARTUP] - {}: bootstrap server is missing", label);
            return;
        }
        String first = bootstrap.split(",")[0].trim();
        String[] parts = first.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : defaultPort;
        checkSocket(label, host, port);
    }

    private void checkUrl(String label, String key, int defaultPort) {
        String raw = environment.getProperty(key, "");
        if (raw.isBlank()) {
            log.warn("[STARTUP] - {}: URL is missing", label);
            return;
        }
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            if (host == null || host.isBlank()) {
                log.warn("[STARTUP] - {}: invalid URL ({})", label, raw);
                return;
            }
            checkSocket(label, host, port);
        } catch (Exception ex) {
            log.warn("[STARTUP] - {}: invalid URL ({})", label, raw);
        }
    }

    private void checkSocket(String label, String host, int port) {
        String endpoint = host + ":" + port;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1200);
            log.info("[STARTUP] + {}: reachable ({})", label, endpoint);
        } catch (Exception ex) {
            log.warn("[STARTUP] - {}: unreachable at {}", label, endpoint);
        }
    }

    private String maskSensitive(String key, String value) {
        if (value == null || value.equals("(not set)")) {
            return value;
        }
        if (key.contains("mongodb.uri")) {
            return value.replaceAll("//([^:@/]+):([^@/]+)@", "//$1:***@");
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
