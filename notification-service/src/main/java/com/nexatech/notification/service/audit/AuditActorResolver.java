package com.nexatech.notification.service.audit;

import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort resolver for the calling user's display info. The notification
 * gateway forwards the original Bearer token (already validated upstream) and
 * an {@code X-User-Id} header — we prefer the token because it carries the
 * friendly {@code preferred_username} / {@code email} claims, and fall back to
 * the header for service-to-service calls.
 */
@Slf4j
@Component
public class AuditActorResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ActorInfo resolveFromCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return ActorInfo.empty();
            }
            HttpServletRequest request = attrs.getRequest();

            ActorInfo fromToken = decodeBearer(request.getHeader("Authorization"));
            if (fromToken.userId() != null) {
                return fromToken;
            }

            String userIdHeader = request.getHeader("X-User-Id");
            if (userIdHeader != null && !userIdHeader.isBlank()) {
                return new ActorInfo(userIdHeader.trim(), null, null);
            }
        } catch (Exception ex) {
            log.debug("Cannot resolve audit actor from current request: {}", ex.getMessage());
        }
        return ActorInfo.empty();
    }

    private ActorInfo decodeBearer(String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return ActorInfo.empty();
        }
        try {
            String[] parts = authHeader.substring(7).trim().split("\\.");
            if (parts.length < 2) {
                return ActorInfo.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = MAPPER.readTree(payload);
            return new ActorInfo(
                    node.path("sub").asText(null),
                    blankToNull(node.path("preferred_username").asText(null)),
                    blankToNull(node.path("email").asText(null)));
        } catch (Exception ex) {
            return ActorInfo.empty();
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record ActorInfo(String userId, String username, String email) {
        public static ActorInfo empty() {
            return new ActorInfo(null, null, null);
        }

        public String displayName() {
            if (username != null) return username;
            if (email != null) return email;
            return userId != null ? userId : "system";
        }
    }
}
