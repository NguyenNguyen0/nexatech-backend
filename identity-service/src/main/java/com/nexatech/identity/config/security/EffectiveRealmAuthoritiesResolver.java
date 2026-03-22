package com.nexatech.identity.config.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Applies explicit-deny override: if {@code X_DENY} is present, {@code X} is removed.
 * Deny markers are not included in the effective authority set.
 * <p>
 * Authorities use the same string as Keycloak realm role names (no {@code ROLE_} prefix).
 */
public final class EffectiveRealmAuthoritiesResolver {

    private static final String DENY_SUFFIX = "_DENY";

    private EffectiveRealmAuthoritiesResolver() {
    }

    public static Collection<GrantedAuthority> resolve(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> deniedBases = new HashSet<>();
        for (GrantedAuthority a : authorities) {
            String auth = a.getAuthority();
            if (auth == null) {
                continue;
            }
            if (auth.endsWith(DENY_SUFFIX) && auth.length() > DENY_SUFFIX.length()) {
                deniedBases.add(auth.substring(0, auth.length() - DENY_SUFFIX.length()));
            }
        }

        List<GrantedAuthority> result = new ArrayList<>();
        for (GrantedAuthority a : authorities) {
            String auth = a.getAuthority();
            if (auth == null) {
                continue;
            }
            if (auth.endsWith(DENY_SUFFIX)) {
                continue;
            }
            if (deniedBases.contains(auth)) {
                continue;
            }
            result.add(new SimpleGrantedAuthority(auth));
        }
        return result;
    }
}
