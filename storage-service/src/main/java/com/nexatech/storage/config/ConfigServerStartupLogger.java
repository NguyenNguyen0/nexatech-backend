package com.nexatech.storage.config;

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
                "minio.endpoint",
                "minio.bucket",
                "minio.region",
                "server.port",
                "management.endpoint.health.probes.enabled"
        ));

        checkUrl("MinIO", "minio.endpoint", 9000);
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
            log.info("[STARTUP] {} = {} [from: {}]", key, value, source);
        }
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
