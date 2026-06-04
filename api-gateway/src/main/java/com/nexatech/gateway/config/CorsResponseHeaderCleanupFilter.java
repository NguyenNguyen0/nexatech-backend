package com.nexatech.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsResponseHeaderCleanupFilter extends OncePerRequestFilter {

    private static final String ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    private static final String EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    private static final String VARY = "Vary";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CorsHeaderResponseWrapper wrappedResponse =
                new CorsHeaderResponseWrapper(response, request.getHeader("Origin"));
        filterChain.doFilter(request, wrappedResponse);
        wrappedResponse.cleanup();
    }

    private static class CorsHeaderResponseWrapper extends HttpServletResponseWrapper {
        private final String origin;

        CorsHeaderResponseWrapper(HttpServletResponse response, String origin) {
            super(response);
            this.origin = origin;
        }

        @Override
        public void addHeader(String name, String value) {
            if (isAllowOrigin(name) && origin != null && !origin.isBlank()) {
                super.setHeader(name, origin);
                return;
            }

            if (isBooleanCorsHeader(name)) {
                super.setHeader(name, "true".equalsIgnoreCase(value) ? "true" : value);
                return;
            }

            if (isCommaListCorsHeader(name)) {
                super.setHeader(name, mergedCommaHeader(name, value));
                return;
            }

            super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if (isAllowOrigin(name) && origin != null && !origin.isBlank()) {
                super.setHeader(name, origin);
                return;
            }

            if (isBooleanCorsHeader(name)) {
                super.setHeader(name, "true".equalsIgnoreCase(value) ? "true" : value);
                return;
            }

            if (isCommaListCorsHeader(name)) {
                super.setHeader(name, mergedCommaHeader(name, value));
                return;
            }

            super.setHeader(name, value);
        }

        void cleanup() {
            if (origin != null && !origin.isBlank() && hasHeaderValue(this, ALLOW_ORIGIN)) {
                super.setHeader(ALLOW_ORIGIN, origin);
            }

            dedupeBooleanHeader(this, ALLOW_CREDENTIALS);
            dedupeCommaHeader(this, EXPOSE_HEADERS);
            dedupeCommaHeader(this, VARY);
        }

        private String mergedCommaHeader(String name, String value) {
            Set<String> unique = uniqueCommaValues(getHeaders(name));
            addCommaValues(unique, value == null ? List.of() : List.of(value));
            return String.join(", ", unique);
        }
    }

    private static void dedupeBooleanHeader(HttpServletResponse response, String header) {
        Collection<String> values = response.getHeaders(header);
        if (values == null || values.isEmpty()) {
            return;
        }

        for (String value : values) {
            if ("true".equalsIgnoreCase(value)) {
                response.setHeader(header, "true");
                return;
            }
        }
        response.setHeader(header, values.iterator().next());
    }

    private static void dedupeCommaHeader(HttpServletResponse response, String header) {
        Collection<String> values = response.getHeaders(header);
        if (values == null || values.isEmpty()) {
            return;
        }

        Set<String> unique = uniqueCommaValues(values);
        if (!unique.isEmpty()) {
            response.setHeader(header, String.join(", ", unique));
        }
    }

    private static boolean hasHeaderValue(HttpServletResponse response, String header) {
        Collection<String> values = response.getHeaders(header);
        return values != null && !values.isEmpty();
    }

    private static Set<String> uniqueCommaValues(Collection<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        addCommaValues(unique, values);
        return unique;
    }

    private static void addCommaValues(Set<String> unique, Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isEmpty())
                    .forEach(unique::add);
        }
    }

    private static boolean isAllowOrigin(String name) {
        return ALLOW_ORIGIN.equalsIgnoreCase(name);
    }

    private static boolean isBooleanCorsHeader(String name) {
        return ALLOW_CREDENTIALS.equalsIgnoreCase(name);
    }

    private static boolean isCommaListCorsHeader(String name) {
        return EXPOSE_HEADERS.equalsIgnoreCase(name) || VARY.equalsIgnoreCase(name);
    }
}
