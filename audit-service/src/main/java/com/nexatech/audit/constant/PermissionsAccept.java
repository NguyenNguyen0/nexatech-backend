package com.nexatech.audit.constant;

import java.util.List;

/**
 * Realm role names that <strong>grant</strong> access (positive permissions).
 * Must match Keycloak realm role names and
 * {@link com.nexatech.audit.config.security.CustomAuthoritiesConverter}.
 */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    /** Audit log access (read-only by design — audit trail is immutable). */
    public static final class Audit {
        private Audit() {
        }

        public static final String READ = "AUDIT_READ";
        public static final String EXPORT = "AUDIT_EXPORT";
    }

    /** All grant permission codes (for admin UI / API catalog). */
    public static List<String> allCodes() {
        return List.of(
                Audit.READ,
                Audit.EXPORT);
    }
}
