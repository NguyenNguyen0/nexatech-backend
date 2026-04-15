package com.nexatech.user.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class EffectiveRealmAuthoritiesResolverTest {

    @Test
    void denyRemovesCorrespondingAllow() {
        var result = EffectiveRealmAuthoritiesResolver.resolve(
                List.of(
                        new SimpleGrantedAuthority("USER_CREATE"),
                        new SimpleGrantedAuthority("USER_CREATE_DENY")));
        assertThat(result).isEmpty();
    }

    @Test
    void allowWithoutDenyUnchanged() {
        var result = EffectiveRealmAuthoritiesResolver.resolve(
                List.of(new SimpleGrantedAuthority("USER_CREATE")));
        assertThat(result).extracting("authority").containsExactly("USER_CREATE");
    }

    @Test
    void denyMarkerNotInEffectiveAuthorities() {
        var result = EffectiveRealmAuthoritiesResolver.resolve(
                List.of(new SimpleGrantedAuthority("USER_CREATE_DENY")));
        assertThat(result).isEmpty();
    }

    @Test
    void multipleIndependentDenies() {
        var result = EffectiveRealmAuthoritiesResolver.resolve(
                List.of(
                        new SimpleGrantedAuthority("A"),
                        new SimpleGrantedAuthority("B"),
                        new SimpleGrantedAuthority("A_DENY")));
        assertThat(result).extracting("authority").containsExactlyInAnyOrder("B");
    }
}
