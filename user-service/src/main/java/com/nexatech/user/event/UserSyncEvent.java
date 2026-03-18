package com.nexatech.user.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSyncEvent {
    
    @KafkaListener(topics = "${spring.kafka.topic.user-registration}")
    public void listenUserRegistration(String message) {
        // Sự kiện đăng ký người dùng ở đây
    }
}
