package com.nexatech.user.event;

import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserConsumer {

    UserService userService;

    // Ở đây là tạo DLT gom các event fail retry nếu thực sự fail đưa vào 1 ổ DLT :v
    @KafkaListener(
            topics = "${spring.kafka.topics.user-registration}",
            containerFactory = "userRegistrationKafkaListenerContainerFactory"
    )
    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 1000, multiplier = 2),
            autoCreateTopics = "true",
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = { AppException.class }
    )
    public void onUserRegistered(UserCreationRequest request) {
        try {
            userService.createUser(request);
        } catch (AppException e) {
            if (e.getErrorCode() == ErrorCode.USER_EMAIL_EXISTS) {
                log.info("User registration skipped (email already present): {}", request.getEmail());
                return;
            }
            throw e;
        }
    }

    @DltHandler
    public void onDltMessage(
            UserCreationRequest request,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) String error) {
        log.error("[DLT][{}] partition={} offset={} key={} error={}",
                topic,
                partition,
                offset,
                key,
                error != null ? error : "unknown");
        log.debug("[DLT][{}] payload={}", topic, request);
    }
}
