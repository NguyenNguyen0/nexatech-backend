package com.nexatech.notification.service;

import com.nexatech.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    public Page<NotificationResponse> getNotifications(UUID userId, Pageable pageable) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
