package com.nexatech.user.constant;

import java.util.List;

/**
 * Realm role names that <strong>deny</strong> a matching grant (suffix {@code _DENY});
 * see {@link com.nexatech.user.config.security.EffectiveRealmAuthoritiesResolver}.
 * Each entry corresponds to a grant in {@link PermissionsAccept} with {@code _DENY} appended.
 */
public final class PermissionsDeny {

    private PermissionsDeny() {
    }

    /** Deny counterparts for {@link PermissionsAccept.User}. */
    public static final class User {
        private User() {
        }

        public static final String READ = "USER_READ_DENY";
        public static final String CREATE = "USER_CREATE_DENY";
        public static final String UPDATE = "USER_UPDATE_DENY";
        public static final String DELETE = "USER_DELETE_DENY";
        public static final String ENABLE = "USER_ENABLE_DENY";
    }

    /** Deny counterparts for {@link PermissionsAccept.Group}. */
    public static final class Group {
        private Group() {
        }

        public static final String READ = "GROUP_READ_DENY";
        public static final String WRITE = "GROUP_WRITE_DENY";
    }

    /** Deny counterparts for {@link PermissionsAccept.RealmRole}. */
    public static final class RealmRole {
        private RealmRole() {
        }

        public static final String READ = "ROLE_READ_DENY";
        public static final String WRITE = "ROLE_WRITE_DENY";
    }

    /** Deny counterparts for {@link PermissionsAccept.Definition}. */
    public static final class Definition {
        private Definition() {
        }

        public static final String READ = "PERMISSION_READ_DENY";
        public static final String WRITE = "PERMISSION_WRITE_DENY";
    }

    /** All deny permission codes (for admin UI / API catalog). */
    public static List<String> allCodes() {
        return List.of(
                User.READ,
                User.CREATE,
                User.UPDATE,
                User.DELETE,
                User.ENABLE,
                Group.READ,
                Group.WRITE,
                RealmRole.READ,
                RealmRole.WRITE,
                Definition.READ,
                Definition.WRITE);
    }
}
