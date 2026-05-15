package com.nexatech.identity.config.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexatech.identity.dto.base.ApiResponse;
import com.nexatech.identity.exception.ErrorCode;
import com.nexatech.identity.service.support.SessionRevocationStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs after Spring Security's BearerTokenAuthenticationFilter.
 *
 * For every request carrying a valid JWT, extracts the Keycloak session-ID
 * from the "sid" claim and checks it against the Redis revocation list.
 * If the session has been explicitly revoked (e.g. "Sign out this device" or
 * "Sign out all devices"), the request is rejected with 401 immediately —
 * even though the JWT signature is still technically valid.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevocationFilter extends OncePerRequestFilter {

    private final SessionRevocationStore revocationStore;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sid = jwtAuth.getToken().getClaimAsString("sid");

            if (sid != null && revocationStore.isRevoked(sid)) {
                log.debug("Rejected revoked session sid={} for {}", sid, request.getRequestURI());
                SecurityContextHolder.clearContext();

                ErrorCode errorCode = ErrorCode.UNAUTHENTICATED;
                response.setStatus(errorCode.getStatusCode().value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        objectMapper.writeValueAsString(
                                ApiResponse.builder()
                                        .code(errorCode.getCode())
                                        .message("Session has been revoked. Please log in again.")
                                        .build()));
                response.flushBuffer();
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
