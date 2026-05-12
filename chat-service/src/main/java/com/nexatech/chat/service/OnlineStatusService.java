package com.nexatech.chat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class OnlineStatusService {

    private static final String ONLINE_KEY_PREFIX = "chat:online:";

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

    private String key(UUID userId) {
        return ONLINE_KEY_PREFIX + userId;
    }
}
