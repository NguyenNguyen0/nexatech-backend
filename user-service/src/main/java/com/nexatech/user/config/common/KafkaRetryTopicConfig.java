package com.nexatech.user.config.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class KafkaRetryTopicConfig {

    @Bean
    public TaskScheduler kafkaRetryTopicTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("kafka-retry-");
        scheduler.initialize();
        return scheduler;
    }
}
