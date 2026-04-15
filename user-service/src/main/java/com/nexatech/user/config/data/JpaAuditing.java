package com.nexatech.user.config.data;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditing {

    /**
     * Current user for {@code createdBy}/{@code updatedBy}: Keycloak {@code sub} (user id), then
     * {@code preferred_username}. Resource server exposes {@link JwtAuthenticationToken}, not raw {@link Jwt}
     * on {@link Authentication#getPrincipal()} in all setups — handle both.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            if (auth instanceof JwtAuthenticationToken jwtAuth) {
                return auditorFromJwt(jwtAuth.getToken());
            }
            if (auth.getPrincipal() instanceof Jwt jwt) {
                return auditorFromJwt(jwt);
            }
            return Optional.empty();
        };
    }

    private static Optional<String> auditorFromJwt(Jwt jwt) {
        if (jwt == null) {
            return Optional.empty();
        }
        String sub = jwt.getSubject();
        if (sub != null && !sub.isBlank()) {
            return Optional.of(sub);
        }
        String preferred = jwt.getClaimAsString("preferred_username");
        if (preferred != null && !preferred.isBlank()) {
            return Optional.of(preferred);
        }
        return Optional.empty();
    }
}
