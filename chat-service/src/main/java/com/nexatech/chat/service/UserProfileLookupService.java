package com.nexatech.chat.service;

import com.nexatech.chat.client.UserServiceClient;
import com.nexatech.chat.client.dto.UserProfileResponse;
import com.nexatech.chat.client.dto.UserServiceApiResponse;
import com.nexatech.chat.dto.response.ParticipantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileLookupService {

    private static final Duration SUCCESS_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration FALLBACK_CACHE_TTL = Duration.ofSeconds(30);

    private final UserServiceClient userServiceClient;
    private final Map<UUID, CacheEntry> userCache = new ConcurrentHashMap<>();

    public ParticipantResponse resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }

        CacheEntry cached = userCache.get(userId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.participant();
        }

        try {
            UserServiceApiResponse<UserProfileResponse> response = userServiceClient.getUserById(userId.toString());
            UserProfileResponse profile = response != null ? response.getResult() : null;
            if (profile == null) {
                return cache(userId, fallbackUser(userId), FALLBACK_CACHE_TTL);
            }

            ParticipantResponse participant = ParticipantResponse.builder()
                    .id(userId)
                    .type("USER")
                    .displayName(displayName(profile, userId))
                    .email(profile.getEmail())
                    .username(profile.getUsername())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
            return cache(userId, participant, SUCCESS_CACHE_TTL);
        } catch (Exception ex) {
            log.debug("Could not resolve user profile for chat participant {}", userId, ex);
            return cache(userId, fallbackUser(userId), FALLBACK_CACHE_TTL);
        }
    }

    public ParticipantResponse resolveGuest(UUID guestId) {
        if (guestId == null) {
            return null;
        }
        return ParticipantResponse.builder()
                .id(guestId)
                .type("GUEST")
                .displayName("Khach " + guestId.toString().substring(0, 8))
                .build();
    }

    private ParticipantResponse fallbackUser(UUID userId) {
        return ParticipantResponse.builder()
                .id(userId)
                .type("USER")
                .displayName("Nguoi dung " + userId.toString().substring(0, 8))
                .build();
    }

    private ParticipantResponse cache(UUID userId, ParticipantResponse participant, Duration ttl) {
        userCache.put(userId, new CacheEntry(participant, Instant.now().plus(ttl)));
        return participant;
    }

    private String displayName(UserProfileResponse profile, UUID fallbackId) {
        String firstName = trimToEmpty(profile.getFirstName());
        String lastName = trimToEmpty(profile.getLastName());
        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        if (profile.getUsername() != null && !profile.getUsername().isBlank()) {
            return profile.getUsername();
        }
        if (profile.getEmail() != null && !profile.getEmail().isBlank()) {
            return profile.getEmail();
        }
        return "Nguoi dung " + fallbackId.toString().substring(0, 8);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private record CacheEntry(ParticipantResponse participant, Instant expiresAt) {
    }
}
