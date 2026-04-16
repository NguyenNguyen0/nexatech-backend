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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();

            Map<String, String> extraHeaders = new HashMap<>();

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

            // realm_access.roles → X-User-Role (first non-default role, or fallback to "USER")
            String role = extractRole(jwt);
            if (role != null) {
                extraHeaders.put(HEADER_USER_ROLE, role);
            }

            if (!extraHeaders.isEmpty()) {
                filterChain.doFilter(new HeaderInjectingRequest(request, extraHeaders), response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return null;
        }
        Object roles = realmAccess.get("roles");
        if (roles instanceof List<?> roleList) {
            return roleList.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(r -> !r.startsWith("default-roles-"))
                    .filter(r -> !"offline_access".equals(r))
                    .filter(r -> !"uma_authorization".equals(r))
                    .findFirst()
                    .orElse(null);
        }
        return null;
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
