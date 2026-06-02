package com.nexatech.gateway.config;

import com.nexatech.gateway.filter.JwtHeaderRelayFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    private static final List<RequestMatcher> PUBLIC_MATCHERS = List.of(
            path("/actuator/**"),
            path("/api/v1/auth"),
            path("/api/v1/auth/**"),
            path("/ws/chat"),
            path("/ws/chat/**"),
            path("/api/v1/cart"),
            path("/api/v1/cart/**"),
            path("/api/v1/checkout"),
            path("/api/v1/orders"),
            path("/api/v1/orders/**"),
            path("/api/v1/returns"),
            path("/api/v1/returns/**"),
            path("/api/v1/payments"),
            path("/api/v1/payments/**"),
            path("/payment/vnpay/**"),
            path("/api/v1/shipping"),
            path("/api/v1/shipping/**"),
            path("/api/v1/promotions"),
            path("/api/v1/promotions/**"),
            path("/api/v1/flash-sales"),
            path("/api/v1/flash-sales/**"),
            path("/api/v1/conversations"),
            path("/api/v1/conversations/**"),
            methodPath(HttpMethod.GET, "/api/v1/categories"),
            methodPath(HttpMethod.GET, "/api/v1/categories/**"),
            methodPath(HttpMethod.GET, "/api/v1/brands"),
            methodPath(HttpMethod.GET, "/api/v1/brands/**"),
            methodPath(HttpMethod.GET, "/api/v1/reviews"),
            methodPath(HttpMethod.GET, "/api/v1/reviews/**"),
            methodPath(HttpMethod.GET, "/api/v1/recommend"),
            methodPath(HttpMethod.GET, "/api/v1/recommend/**"),
            methodPath(HttpMethod.GET, "/api/v1/news"),
            methodPath(HttpMethod.GET, "/api/v1/news/**"),
            methodPath(HttpMethod.GET, "/api/v1/agent/health"),
            methodPath(HttpMethod.GET, "/api/v1/agent/copilotkit/info"),
            methodPath(HttpMethod.GET, "/api/v1/mcp/health"),
            methodPath(HttpMethod.POST, "/api/v1/checkout"),
            methodPath(HttpMethod.POST, "/api/v1/orders/*/continue-payment"),
            methodPath(HttpMethod.GET, "/api/v1/payments/vnpay/ipn"),
            methodPath(HttpMethod.GET, "/api/v1/payments/vnpay/return"),
            methodPath(HttpMethod.GET, "/payment/vnpay/ipn"),
            methodPath(HttpMethod.GET, "/payment/vnpay/return"),
            methodPath(HttpMethod.GET, "/api/v1/shipping/orders/*"),
            methodPath(HttpMethod.GET, "/api/v1/shipping/shipments/*/tracking")
    );

    private static final RequestMatcher PUBLIC_ENDPOINTS = request ->
            HttpMethod.OPTIONS.matches(request.getMethod())
                    || isPublicProductRequest(request.getMethod(), request.getRequestURI())
                    || PUBLIC_MATCHERS.stream().anyMatch(matcher -> matcher.matches(request));

    @Bean
    @Order(1)
    public SecurityFilterChain gatewayRoutes(HttpSecurity http, JwtHeaderRelayFilter jwtHeaderRelayFilter) throws Exception {
        stateless(http);
        http.authorizeHttpRequests(a -> a
                .anyRequest().access((authentication, context) ->
                        new AuthorizationDecision(PUBLIC_ENDPOINTS.matches(context.getRequest())
                                || authentication.get().isAuthenticated())))
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

    private static RequestMatcher path(String pattern) {
        return request -> pathMatches(pattern, request.getRequestURI());
    }

    private static RequestMatcher methodPath(HttpMethod method, String pattern) {
        return request -> method.matches(request.getMethod()) && pathMatches(pattern, request.getRequestURI());
    }

    private static boolean isPublicProductRequest(String method, String path) {
        return HttpMethod.GET.matches(method)
                && ("/api/v1/products".equals(path)
                    || (path.startsWith("/api/v1/products/")
                        && !path.startsWith("/api/v1/products/wishlist")));
    }

    private static boolean pathMatches(String pattern, String path) {
        if (pattern.endsWith("/**")) {
            String base = pattern.substring(0, pattern.length() - 3);
            return path.equals(base) || path.startsWith(base + "/");
        }

        if (!pattern.contains("*")) {
            return pattern.equals(path);
        }

        String[] patternParts = pathParts(pattern);
        String[] pathParts = pathParts(path);
        if (patternParts.length != pathParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            if (!"*".equals(patternParts[i]) && !patternParts[i].equals(pathParts[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] pathParts(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.isBlank() ? new String[0] : normalized.split("/");
    }
}
