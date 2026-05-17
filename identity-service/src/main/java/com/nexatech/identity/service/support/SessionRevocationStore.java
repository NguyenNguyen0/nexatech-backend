package com.nexatech.identity.service.support;

import java.time.Duration;
import java.util.Collection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis-backed revocation list for Keycloak session IDs (the "sid" JWT claim).
 *
 * When a session is deleted, its ID is written here with a TTL equal to the
 * access-token lifetime. SessionRevocationFilter reads this list on every
 * authenticated request and immediately rejects tokens whose session was revoked.
 *
 * Fail-open design: if Redis is unavailable (e.g. wrong password, network issue),
 * the check returns false (allow request) rather than crashing the service.
 * Configure REDIS_PASSWORD correctly to activate the security guarantee.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevocationStore {

    private static final String KEY_PREFIX = "session:revoked:";

    private final StringRedisTemplate redisTemplate;

    @Value("${session.revocation.ttl-minutes:15}")
    private long ttlMinutes;

    public void revoke(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, "1", Duration.ofMinutes(ttlMinutes));
            log.debug("Session revoked in Redis: {}", sessionId);
        } catch (Exception e) {
            log.warn("Redis unavailable — session {} NOT added to revocation list: {}", sessionId, e.getMessage());
        }
    }

    public void revokeAll(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return;
        sessionIds.forEach(this::revoke);
    }

    /**
     * Returns true if the session is on the revocation list.
     * Returns false (fail-open) if Redis is unreachable so the service keeps running.
     */
    public boolean isRevoked(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + sessionId));
        } catch (Exception e) {
            log.warn("Redis unavailable — revocation check skipped for session {}: {}", sessionId, e.getMessage());
            return false;
        }
    }
}
