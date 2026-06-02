package com.nexatech.gateway.config;

import com.nexatech.gateway.filter.JwtHeaderRelayFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain publicEndpoints(HttpSecurity http, JwtHeaderRelayFilter jwtHeaderRelayFilter) throws Exception {
        http.securityMatcher(
                "/api/v1/agent/health",
                "/api/v1/agent/copilotkit/info",
                "/api/v1/mcp/health"
            )
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .addFilterBefore(jwtHeaderRelayFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain gatewayRoutes(HttpSecurity http, JwtHeaderRelayFilter jwtHeaderRelayFilter) throws Exception {
        stateless(http);
        http.authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/**", "/api/v1/auth", "/api/v1/auth/**", "/ws/chat", "/ws/chat/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/orders/my").authenticated()
                .requestMatchers(
                    "/api/v1/cart",
                    "/api/v1/cart/**",
                    "/api/v1/checkout",
                    "/api/v1/orders",
                    "/api/v1/orders/**",
                    "/api/v1/returns",
                    "/api/v1/returns/**",
                    "/api/v1/admin/orders",
                    "/api/v1/admin/orders/**",
                    "/api/v1/payments",
                    "/api/v1/payments/**",
                    "/api/v1/admin/payments",
                    "/api/v1/admin/payments/**",
                    "/payment/vnpay/**",
                    "/api/v1/shipping",
                    "/api/v1/shipping/**",
                    "/api/v1/promotions",
                    "/api/v1/promotions/**",
                    "/api/v1/flash-sales",
                    "/api/v1/flash-sales/**",
                    "/api/v1/conversations",
                    "/api/v1/conversations/**")
                .permitAll()
                .requestMatchers("/api/v1/products/wishlist/**").authenticated()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/products",
                    "/api/v1/products/**",
                    "/api/v1/categories",
                    "/api/v1/categories/**",
                    "/api/v1/brands",
                    "/api/v1/brands/**",
                    "/api/v1/reviews",
                    "/api/v1/reviews/**",
                    "/api/v1/recommend",
                    "/api/v1/recommend/**",
                    "/api/v1/news",
                    "/api/v1/news/**",
                    "/api/v1/conversations/current",
                    "/api/v1/conversations/*/messages")
                .permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/conversations/current",
                    "/api/v1/conversations/*/messages")
                .permitAll()
                .requestMatchers(HttpMethod.PUT,
                    "/api/v1/conversations/*/messages/*/delivered",
                    "/api/v1/conversations/*/messages/*/seen")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/promotions",
                    "/api/v1/promotions/**",
                    "/api/v1/flash-sales",
                    "/api/v1/flash-sales/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/agent/health",
                    "/api/v1/agent/copilotkit/info",
                    "/api/v1/mcp/health")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/checkout").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/orders/*/continue-payment").permitAll()
                // VNPay callbacks — server-to-server IPN and browser return (no auth token)
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/payments/vnpay/ipn",
                    "/api/v1/payments/vnpay/return",
                    "/payment/vnpay/ipn",
                    "/payment/vnpay/return")
                .permitAll()
                // Guest order status polling after checkout
                .requestMatchers(HttpMethod.GET, "/api/v1/orders/*").permitAll()
                // Guest order detail can display read-only shipment tracking.
                .requestMatchers(HttpMethod.GET,
                    "/api/v1/shipping/orders/*",
                    "/api/v1/shipping/shipments/*/tracking")
                .permitAll()
                .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .addFilterAfter(jwtHeaderRelayFilter, BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<JwtHeaderRelayFilter> jwtHeaderRelayFilterRegistration(
            JwtHeaderRelayFilter jwtHeaderRelayFilter) {
        FilterRegistrationBean<JwtHeaderRelayFilter> registration = new FilterRegistrationBean<>(jwtHeaderRelayFilter);
        registration.setEnabled(false);
        return registration;
    }

    private static void stateless(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
