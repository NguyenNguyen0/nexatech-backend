package com.nexatech.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventConsumer {

    @KafkaListener(topics = "user.events", groupId = "user-service-group")
    public void handleUserEvent(String message) {
        log.info("Received user event: {}", message);
    }
}
