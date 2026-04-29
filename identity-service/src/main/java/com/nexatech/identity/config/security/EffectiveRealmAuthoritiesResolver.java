package com.nexatech.identity.config.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private static final String DENY_SUFFIX_UPPER = DENY_SUFFIX.toUpperCase(Locale.ROOT);

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
            String authUpper = auth.toUpperCase(Locale.ROOT);
            if (authUpper.endsWith(DENY_SUFFIX_UPPER) && authUpper.length() > DENY_SUFFIX_UPPER.length()) {
                deniedBases.add(authUpper.substring(0, authUpper.length() - DENY_SUFFIX_UPPER.length()));
            }
        }

        List<GrantedAuthority> result = new ArrayList<>();
        for (GrantedAuthority a : authorities) {
            String auth = a.getAuthority();
            if (auth == null) {
                continue;
            }
            String authUpper = auth.toUpperCase(Locale.ROOT);
            if (authUpper.endsWith(DENY_SUFFIX_UPPER)) {
                continue;
            }
            if (deniedBases.contains(authUpper)) {
                continue;
            }
            // Normalize to CAPLOCK so downstream checks are stable.
            result.add(new SimpleGrantedAuthority(authUpper));
        }
        return result;
    }
}
