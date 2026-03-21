package com.nexatech.identity.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
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
        };

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity.authorizeHttpRequests(request -> request
                                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                                .requestMatchers(HttpMethod.POST, "/users").permitAll()
                                .anyRequest()
                                .authenticated());

                httpSecurity.oauth2ResourceServer(oauth2 -> oauth2.jwt(
                                jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));
                httpSecurity.csrf(AbstractHttpConfigurer::disable);

                return httpSecurity.build();
        }

        @Bean
        public JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(new CustomAuthoritiesConverter());

                return jwtAuthenticationConverter;
        }
}
