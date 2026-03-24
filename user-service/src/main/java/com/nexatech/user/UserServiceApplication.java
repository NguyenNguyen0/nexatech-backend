package com.nexatech.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;

@EnableDiscoveryClient
@SpringBootApplication
@EnableFeignClients
@EnableKafkaRetryTopic
// @EnableWebSecurity
// @EnableMethodSecurity
// @EnableJpaAuditing
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
