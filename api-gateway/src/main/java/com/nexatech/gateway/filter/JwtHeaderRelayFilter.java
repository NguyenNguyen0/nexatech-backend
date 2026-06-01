package com.nexatech.gateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Extracts claims from the authenticated JWT and forwards them as
 * {@code X-User-Id}, {@code X-User-Role}, and {@code X-User-Email} headers
 * to downstream services via Spring Cloud Gateway.
 */
@Component
public class JwtHeaderRelayFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_EMAIL = "X-User-Email";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Map<String, String> extraHeaders = new HashMap<>();
        String correlationId = Optional.ofNullable(request.getHeader(HEADER_CORRELATION_ID))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
        extraHeaders.put(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            // sub claim → X-User-Id
            String sub = jwt.getSubject();
            if (sub != null) {
                extraHeaders.put(HEADER_USER_ID, sub);
            }

            // email claim → X-User-Email
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                extraHeaders.put(HEADER_USER_EMAIL, email);
            }

            // realm_access.roles → X-User-Role (first non-default role, fallback to "USER")
            String role = extractRole(jwt);
            extraHeaders.put(HEADER_USER_ROLE, role != null ? role : "USER");

        }

        filterChain.doFilter(new HeaderInjectingRequest(request, extraHeaders), response);
    }

    private String extractRole(Jwt jwt) {
        List<String> roles = new ArrayList<>();

        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null) {
            collectRoles(realmAccess.get("roles"), roles);
        }

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null) {
            resourceAccess.values().forEach(resource -> {
                if (resource instanceof Map<?, ?> resourceMap) {
                    collectRoles(resourceMap.get("roles"), roles);
                }
            });
        }

        Set<String> normalizedRoles = new LinkedHashSet<>();
        roles.stream()
                .map(this::normalizeRole)
                .filter(Objects::nonNull)
                .forEach(normalizedRoles::add);

        for (String preferredRole : List.of("ADMIN", "MANAGER", "STAFF", "WAREHOUSE", "ACCOUNTANT", "CUSTOMER")) {
            if (normalizedRoles.contains(preferredRole)) {
                return preferredRole;
            }
        }

        return normalizedRoles.stream().findFirst().orElse(null);
    }

    private void collectRoles(Object rawRoles, List<String> roles) {
        if (rawRoles instanceof Collection<?> roleCollection) {
            roleCollection.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .forEach(roles::add);
        }
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        if (normalized.startsWith("SCOPE_")) {
            normalized = normalized.substring("SCOPE_".length());
        }
        if (normalized.isBlank()
                || normalized.startsWith("DEFAULT-ROLES-")
                || "OFFLINE_ACCESS".equals(normalized)
                || "UMA_AUTHORIZATION".equals(normalized)) {
            return null;
        }
        return normalized;
    }

    /**
     * Wraps the original request to inject additional headers without mutating the original.
     */
    private static class HeaderInjectingRequest extends HttpServletRequestWrapper {

        private final Map<String, String> extraHeaders;

        HeaderInjectingRequest(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            String extra = extraHeaders.get(name);
            return extra != null ? extra : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String extra = extraHeaders.get(name);
            if (extra != null) {
                return Collections.enumeration(List.of(extra));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }
    }
}
