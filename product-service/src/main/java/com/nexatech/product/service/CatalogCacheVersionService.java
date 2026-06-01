package com.nexatech.product.service;

import com.nexatech.product.dto.response.CatalogCacheVersionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class CatalogCacheVersionService {

    private static final String VERSION_KEY = "product-service:catalog-cache-version";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final AtomicLong fallbackVersion = new AtomicLong(Instant.now().toEpochMilli());

    public CatalogCacheVersionService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    public CatalogCacheVersionResponse currentVersion() {
        return CatalogCacheVersionResponse.builder()
                .version(readVersion())
                .build();
    }

    public String bumpVersion(String reason) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                Long nextVersion = redisTemplate.opsForValue().increment(VERSION_KEY);
                if (nextVersion != null) {
                    fallbackVersion.updateAndGet(current -> Math.max(current, nextVersion));
                    log.info("Catalog cache version bumped: version={} reason={}", nextVersion, reason);
                    return String.valueOf(nextVersion);
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to bump catalog cache version in Redis: {}", exception.getMessage());
            }
        }

        long localVersion = fallbackVersion.updateAndGet(current -> Math.max(current + 1, Instant.now().toEpochMilli()));
        log.info("Catalog cache version bumped locally: version={} reason={}", localVersion, reason);
        return String.valueOf(localVersion);
    }

    private String readVersion() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                String current = redisTemplate.opsForValue().get(VERSION_KEY);
                if (StringUtils.hasText(current)) {
                    syncFallback(current);
                    return current;
                }

                String initialVersion = String.valueOf(fallbackVersion.get());
                redisTemplate.opsForValue().setIfAbsent(VERSION_KEY, initialVersion);
                String stored = redisTemplate.opsForValue().get(VERSION_KEY);
                if (StringUtils.hasText(stored)) {
                    syncFallback(stored);
                    return stored;
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to read catalog cache version from Redis: {}", exception.getMessage());
            }
        }

        return String.valueOf(fallbackVersion.get());
    }

    private void syncFallback(String version) {
        try {
            long numericVersion = Long.parseLong(version);
            fallbackVersion.updateAndGet(current -> Math.max(current, numericVersion));
        } catch (NumberFormatException ignored) {
            // Keep the local fallback as-is when the shared version is not numeric.
        }
    }
}
