package com.nexatech.user.constant;

/**
 * Authority strings = Keycloak <strong>realm role names</strong> (no {@code ROLE_} prefix).
 * {@link com.nexatech.user.config.security.CustomAuthoritiesConverter} maps each JWT realm role to a
 * {@link org.springframework.security.core.GrantedAuthority} with the same string (e.g. {@code USER_READ}).
 * <p>
 * Nested types group permissions by area only.
 */
public final class Permissions {

    private Permissions() {
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

    /** {@link com.nexatech.user.controller.GroupController} */
    public static final class Group {
        private Group() {
        }

        public static final String READ = "GROUP_READ";
        public static final String WRITE = "GROUP_WRITE";
    }

    /**
     * Keycloak realm role CRUD ({@link com.nexatech.user.controller.RoleController}).
     * Realm role names in Keycloak: {@code ROLE_READ}, {@code ROLE_WRITE} (names chosen to avoid clashing with {@code READ}/{@code WRITE} alone).
     */
    public static final class RealmRole {
        private RealmRole() {
        }

        public static final String READ = "ROLE_READ";
        public static final String WRITE = "ROLE_WRITE";
    }

    /** Keycloak permission-role definitions ({@link com.nexatech.user.controller.PermissionController}). */
    public static final class Definition {
        private Definition() {
        }

        public static final String READ = "PERMISSION_READ";
        public static final String WRITE = "PERMISSION_WRITE";
    }
}
