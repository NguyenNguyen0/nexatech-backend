package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.model.Notification;
import com.nexatech.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return notificationRepository.findByUserId(userId, sorted)
                .map(this::toResponse);
    }

    public Notification createNotification(UUID userId, Notification.NotificationType type,
                                           String title, String message) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .channel("IN_APP")
                .subject(title)
                .content(message)
                .status(Notification.NotificationStatus.SENT)
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found: " + notificationId));
        notification.setStatus(Notification.NotificationStatus.READ);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepository.updateStatusByUserId(userId, Notification.NotificationStatus.READ);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType())
                .channel(notification.getChannel())
                .subject(notification.getSubject())
                .content(notification.getContent())
                .status(notification.getStatus())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
