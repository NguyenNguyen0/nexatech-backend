package com.nexatech.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;

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
        stringRedisTemplate.opsForValue().set(key(userId), "1", presenceTtl);
    }

    public void markOffline(UUID userId) {
        if (userId == null) {
            return;
        }
        stringRedisTemplate.delete(key(userId));
        stringRedisTemplate.opsForSet().remove(ONLINE_AGENTS_KEY, userId.toString());
    }

    public boolean isOnline(UUID userId) {
        if (userId == null) {
            return false;
        }
        Boolean exists = stringRedisTemplate.hasKey(key(userId));
        return Boolean.TRUE.equals(exists);
    }

    public void refreshPresence(UUID userId) {
        markOnline(userId);
    }

    public void markAgentOnline(UUID agentId) {
        if (agentId == null) {
            return;
        }
        markOnline(agentId);
        stringRedisTemplate.opsForSet().add(ONLINE_AGENTS_KEY, agentId.toString());
    }

    public Set<UUID> getOnlineAgentIds() {
        Set<String> rawAgentIds = stringRedisTemplate.opsForSet().members(ONLINE_AGENTS_KEY);
        if (rawAgentIds == null || rawAgentIds.isEmpty()) {
            return Set.of();
        }

        return rawAgentIds.stream()
                .map(this::parseUuid)
                .filter(agentId -> agentId != null && isOnline(agentId))
                .collect(Collectors.toSet());
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
