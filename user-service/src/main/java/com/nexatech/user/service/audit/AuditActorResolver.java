package com.nexatech.user.service.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Best-effort resolver that pulls the calling user's display info from the
 * Spring Security {@link JwtAuthenticationToken} populated by the OAuth2
 * resource server filter. Falls back to {@code system} for internal calls.
 */
@Slf4j
@Component
public class AuditActorResolver {

    public ActorInfo resolveFromSecurityContext() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String sub = jwt.getSubject();
                String username = jwt.getClaimAsString("preferred_username");
                String email = jwt.getClaimAsString("email");
                return new ActorInfo(sub, blankToNull(username), blankToNull(email));
            }
        } catch (Exception ex) {
            log.debug("Cannot resolve audit actor from SecurityContext: {}", ex.getMessage());
        }
        return ActorInfo.empty();
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
