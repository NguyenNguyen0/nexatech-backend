package com.nexatech.identity.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

public class CustomAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";

    /**
     * Keycloak realm roles to omit from {@code Authentication#getAuthorities()} (not business roles).
     */
    private static final Set<String> EXCLUDED_REALM_ROLES = Set.of(
            "offline_access",
            "uma_authorization");

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        Map<String, Object> realmAccessMap = source.getClaimAsMap(REALM_ACCESS);
        if (realmAccessMap == null) {
            return Collections.emptyList();
        }

        Object roles = realmAccessMap.get(ROLES);

        if (roles instanceof List<?> stringRoles) {
            List<GrantedAuthority> rawAuthorities = stringRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> !isKeycloakDefaultRealmRole(role))
                    .map(role -> new SimpleGrantedAuthority(role))
                    .collect(Collectors.toList());
            return EffectiveRealmAuthoritiesResolver.resolve(rawAuthorities);
        }

        return Collections.emptyList();
    }

    private static boolean isKeycloakDefaultRealmRole(String realmRole) {
        if (realmRole == null) {
            return true;
        }
        String normalized = realmRole.toLowerCase(Locale.ROOT);
        return EXCLUDED_REALM_ROLES.contains(normalized) || normalized.startsWith("default-roles-");
    }
}
