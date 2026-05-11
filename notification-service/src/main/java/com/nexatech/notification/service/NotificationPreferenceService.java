package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationPreferenceResponse;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.model.NotificationPreference;
import com.nexatech.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    /**
     * Get all preferences for a user.
     * If the user has no preferences yet, create defaults for all notification types.
     */
    @Transactional
    public List<NotificationPreferenceResponse> getPreferences(UUID userId) {
        List<NotificationPreference> preferences = preferenceRepository.findByUserId(userId);

        if (preferences.isEmpty()) {
            preferences = createDefaultPreferences(userId);
        }

        return preferences.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Update a single preference for a user.
     */
    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId,
                                                            Notification.NotificationType type,
                                                            boolean emailEnabled,
                                                            boolean pushEnabled) {
        NotificationPreference preference = preferenceRepository
                .findByUserIdAndNotificationType(userId, type)
                .orElseGet(() -> NotificationPreference.builder()
                        .userId(userId)
                        .notificationType(type)
                        .build());

        preference.setEmailEnabled(emailEnabled);
        preference.setPushEnabled(pushEnabled);
        preference = preferenceRepository.save(preference);

        log.info("Updated preference for userId={} type={}: email={}, push={}",
                userId, type, emailEnabled, pushEnabled);
        return toResponse(preference);
    }

    /**
     * Check if email is enabled for a given user and notification type.
     * Returns true by default if no preference exists (opt-out model).
     */
    public boolean isEmailEnabled(UUID userId, Notification.NotificationType type) {
        return preferenceRepository.findByUserIdAndNotificationType(userId, type)
                .map(NotificationPreference::isEmailEnabled)
                .orElse(true);
    }

    /**
     * Check if push is enabled for a given user and notification type.
     * Returns true by default if no preference exists (opt-out model).
     */
    public boolean isPushEnabled(UUID userId, Notification.NotificationType type) {
        return preferenceRepository.findByUserIdAndNotificationType(userId, type)
                .map(NotificationPreference::isPushEnabled)
                .orElse(true);
    }

    private List<NotificationPreference> createDefaultPreferences(UUID userId) {
        List<NotificationPreference> defaults = Arrays.stream(Notification.NotificationType.values())
                .map(type -> NotificationPreference.builder()
                        .userId(userId)
                        .notificationType(type)
                        .emailEnabled(true)
                        .pushEnabled(true)
                        .build())
                .toList();
        return preferenceRepository.saveAll(defaults);
    }

    private NotificationPreferenceResponse toResponse(NotificationPreference pref) {
        return NotificationPreferenceResponse.builder()
                .id(pref.getId())
                .notificationType(pref.getNotificationType())
                .emailEnabled(pref.isEmailEnabled())
                .pushEnabled(pref.isPushEnabled())
                .build();
    }
}
