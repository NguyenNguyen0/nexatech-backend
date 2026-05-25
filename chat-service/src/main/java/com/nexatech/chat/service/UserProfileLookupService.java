package com.nexatech.chat.service;

import com.nexatech.chat.client.UserServiceClient;
import com.nexatech.chat.client.dto.UserProfileResponse;
import com.nexatech.chat.client.dto.UserServiceApiResponse;
import com.nexatech.chat.dto.response.ParticipantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileLookupService {

    private final UserServiceClient userServiceClient;

    public ParticipantResponse resolveUser(UUID userId) {
        if (userId == null) {
            return null;
        }

        try {
            UserServiceApiResponse<UserProfileResponse> response = userServiceClient.getUserById(userId.toString());
            UserProfileResponse profile = response != null ? response.getResult() : null;
            if (profile == null) {
                return fallbackUser(userId);
            }

            return ParticipantResponse.builder()
                    .id(userId)
                    .type("USER")
                    .displayName(displayName(profile, userId))
                    .email(profile.getEmail())
                    .username(profile.getUsername())
                    .avatarUrl(profile.getAvatarUrl())
                    .build();
        } catch (Exception ex) {
            log.debug("Could not resolve user profile for chat participant {}", userId, ex);
            return fallbackUser(userId);
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
}
