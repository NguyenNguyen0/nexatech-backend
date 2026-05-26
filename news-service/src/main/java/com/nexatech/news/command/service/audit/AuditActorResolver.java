package com.nexatech.news.command.service.audit;

import java.util.Base64;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort resolver that extracts the calling user's display info from the
 * Bearer token attached to the current HTTP request. The gateway has already
 * validated the token, so we only decode the JWT payload for display purposes —
 * never for authorisation.
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
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return ActorInfo.empty();
            }
            String token = authHeader.substring(7).trim();
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return ActorInfo.empty();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode node = MAPPER.readTree(payload);
            String sub = node.path("sub").asText(null);
            String username = node.path("preferred_username").asText(null);
            String email = node.path("email").asText(null);
            return new ActorInfo(sub, blankToNull(username), blankToNull(email));
        } catch (Exception ex) {
            log.debug("Cannot resolve audit actor from current request: {}", ex.getMessage());
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
