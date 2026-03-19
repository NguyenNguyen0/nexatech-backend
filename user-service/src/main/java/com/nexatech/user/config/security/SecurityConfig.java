package com.nexatech.user.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@Configuration
@EnableWebSecurity
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

        private static final String[] PUBLIC_ENDPOINTS = {
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/users/**",
                        "/roles/**",
                        "/permissions/**",
                        "/addresses/**" };

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http.csrf(csrf -> csrf.disable());
                http.authorizeHttpRequests(auth -> auth
                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                                .anyRequest().permitAll());
                return http.build();
        }
}
