package com.nexatech.chat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
public class OnlineStatusService {

    private static final String ONLINE_KEY_PREFIX = "chat:online:";
    private static final String ONLINE_AGENTS_KEY = "chat:online:agents";

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration presenceTtl;

    public OnlineStatusService(StringRedisTemplate stringRedisTemplate,
                               @Value("${app.presence-ttl-seconds:90}") long presenceTtlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.presenceTtl = Duration.ofSeconds(presenceTtlSeconds);
    }

    public void markOnline(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key(userId), "1", presenceTtl);
        } catch (Exception e) {
            log.warn("Redis unavailable - cannot mark user {} online: {}", userId, e.getMessage());
        }
    }

    public void markOffline(UUID userId) {
        if (userId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(key(userId));
            stringRedisTemplate.opsForSet().remove(ONLINE_AGENTS_KEY, userId.toString());
        } catch (Exception e) {
            log.warn("Redis unavailable - cannot mark user {} offline: {}", userId, e.getMessage());
        }
    }

    public boolean isOnline(UUID userId) {
        if (userId == null) {
            return false;
        }
        try {
            Boolean exists = stringRedisTemplate.hasKey(key(userId));
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Redis unavailable - isOnline check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public void refreshPresence(UUID userId) {
        markOnline(userId);
    }

    public void markAgentOnline(UUID agentId) {
        if (agentId == null) {
            return;
        }
        try {
            markOnline(agentId);
            stringRedisTemplate.opsForSet().add(ONLINE_AGENTS_KEY, agentId.toString());
        } catch (Exception e) {
            log.warn("Redis unavailable - cannot mark agent {} online: {}", agentId, e.getMessage());
        }
    }

    public Set<UUID> getOnlineAgentIds() {
        try {
            Set<String> rawAgentIds = stringRedisTemplate.opsForSet().members(ONLINE_AGENTS_KEY);
            if (rawAgentIds == null || rawAgentIds.isEmpty()) {
                return Set.of();
            }
            return rawAgentIds.stream()
                    .map(this::parseUuid)
                    .filter(agentId -> agentId != null && isOnline(agentId))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("Redis unavailable - getOnlineAgentIds returning empty: {}", e.getMessage());
            return Set.of();
        }
    }

    private String key(UUID userId) {
        return ONLINE_KEY_PREFIX + userId;
    }

    private UUID parseUuid(String rawValue) {
        try {
            return UUID.fromString(rawValue);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
