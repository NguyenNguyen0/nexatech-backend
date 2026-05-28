package com.nexatech.identity.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private static final String[] PUBLIC_ENDPOINTS = {
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/auth/openid-configuration",
                        "/auth/introspect",
                        "/auth/logout",
                        "/auth/refresh",
                        "/auth/login",
                        "/auth/social-media",
                        "/auth/register",
                        "/auth/forgot-password",
        };

        private final SessionRevocationFilter sessionRevocationFilter;

        @Bean
        @Order(1)
        public SecurityFilterChain publicFilterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity
                                .securityMatcher(PUBLIC_ENDPOINTS)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                                .csrf(AbstractHttpConfigurer::disable);

                return httpSecurity.build();
        }

        @Bean
        @Order(2)
        public SecurityFilterChain internalFilterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity
                                .securityMatcher("/internal/**")
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(request -> request.anyRequest().permitAll())
                                .csrf(AbstractHttpConfigurer::disable);

                return httpSecurity.build();
        }

        @Bean
        @Order(3)
        public SecurityFilterChain protectedFilterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(request -> request
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                                jwtConfigurer -> jwtConfigurer
                                                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()))
                                // Runs after JWT signature/expiry validation; rejects revoked session IDs
                                .addFilterAfter(sessionRevocationFilter, BearerTokenAuthenticationFilter.class)
                                .csrf(AbstractHttpConfigurer::disable);

                return httpSecurity.build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new CustomAuthoritiesConverter());

                return jwtAuthenticationConverter;
        }
}
