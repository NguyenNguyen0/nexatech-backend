package com.nexatech.notification.config.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class CustomAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final Set<String> EXCLUDED_REALM_ROLES = Set.of("offline_access", "uma_authorization");

    @Override
    public Collection<GrantedAuthority> convert(Jwt source) {
        Map<String, Object> realmAccessMap = source.getClaimAsMap(REALM_ACCESS);
        if (realmAccessMap == null) {
            return Collections.emptyList();
        }
        Object roles = realmAccessMap.get(ROLES);
        if (roles instanceof List<?> stringRoles) {
            return stringRoles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> !EXCLUDED_REALM_ROLES.contains(role) && !role.startsWith("default-roles-"))
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
