package com.nexatech.user.constant;

import java.util.List;

/**
 * Realm role names that <strong>grant</strong> access (positive permissions).
 * Must match Keycloak realm role names and
 * {@link com.nexatech.user.config.security.CustomAuthoritiesConverter}.
 */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    /** {@link com.nexatech.user.controller.UserController} */
    public static final class User {
        private User() {
        }

        public static final String READ = "USER_READ";
        public static final String CREATE = "USER_CREATE";
        public static final String UPDATE = "USER_UPDATE";
        public static final String DELETE = "USER_DELETE";
        public static final String ENABLE = "USER_ENABLE";
    }

    /**
     * Keycloak realm role CRUD
     * ({@link com.nexatech.user.controller.RoleController}).
     */
    public static final class RealmRole {
        private RealmRole() {
        }

        public static final String READ = "ROLE_READ";
        public static final String WRITE = "ROLE_WRITE";
    }

    /**
     * Keycloak permission-role definitions
     * ({@link com.nexatech.user.controller.PermissionController}).
     */
    public static final class Definition {
        private Definition() {
        }

        public static final String READ = "PERMISSION_READ";
        public static final String WRITE = "PERMISSION_WRITE";
    }

    /** All grant permission codes (for admin UI / API catalog). */
    public static List<String> allCodes() {
        return List.of(
                User.READ,
                User.CREATE,
                User.UPDATE,
                User.DELETE,
                User.ENABLE,
                RealmRole.READ,
                RealmRole.WRITE,
                Definition.READ,
                Definition.WRITE);
    }
}
