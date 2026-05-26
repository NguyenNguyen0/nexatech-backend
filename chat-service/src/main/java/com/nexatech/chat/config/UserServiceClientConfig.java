package com.nexatech.chat.config;

import feign.Request;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class UserServiceClientConfig {

    @Bean
    public Request.Options userServiceRequestOptions() {
        return new Request.Options(
                500,
                TimeUnit.MILLISECONDS,
                800,
                TimeUnit.MILLISECONDS,
                true);
    }
}
