package com.nexatech.user.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires the current user to hold the given authority (same string as Keycloak realm role, e.g. {@code USER_READ}).
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * Authority string, typically {@link com.nexatech.user.constant.PermissionsAccept}.*.
     */
    String value();
}
