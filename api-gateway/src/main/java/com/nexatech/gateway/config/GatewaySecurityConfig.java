package com.nexatech.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain publicRoutes(HttpSecurity http) throws Exception {
        stateless(http);
        http.securityMatcher("/actuator/**", "/api/v1/auth/**", "/ws/chat", "/ws/chat/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    public SecurityFilterChain jwtRoutes(HttpSecurity http) throws Exception {
        stateless(http);
        http.authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/products/wishlist/**").authenticated()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/products/**",
                    "/api/v1/categories/**",
                    "/api/v1/brands/**",
                    "/api/v1/reviews/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/agent/health",
                    "/api/v1/mcp/health",
                    "/api/v1/recommendations/health")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout").permitAll()
                // VNPay callbacks — server-to-server IPN and browser return (no auth token)
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/payments/vnpay/ipn",
                    "/api/v1/payments/vnpay/return")
                .permitAll()
                // Guest order status polling after checkout
                .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                .requestMatchers("/api/v1/news/**").permitAll()
                .requestMatchers("/api/v1/conversations/**").permitAll()
                .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
        return http.build();
    }

    private static void stateless(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
