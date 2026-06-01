package com.nexatech.inventory.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class PermissionAuthorizationAspect {

    @Before("@annotation(requiresPermission)")
    public void checkPermission(RequiresPermission requiresPermission) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }

        String required = requiresPermission.value();
        boolean granted = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(required::equals);
        if (!granted) {
            throw new AccessDeniedException("Missing authority: " + required);
        }
    }
}
