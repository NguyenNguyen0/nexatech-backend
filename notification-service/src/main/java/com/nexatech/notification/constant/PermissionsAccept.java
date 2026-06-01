package com.nexatech.notification.constant;

import java.util.List;

/** Keycloak realm role names that grant access in notification-service. */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    public static final class Notification {
        private Notification() {
        }

        public static final String READ = "NOTIFICATION_READ";
        public static final String WRITE = "NOTIFICATION_WRITE";
        public static final String ADMIN_READ = "NOTIFICATION_ADMIN_READ";
    }

    public static List<String> allCodes() {
        return List.of(Notification.READ, Notification.WRITE, Notification.ADMIN_READ);
    }
}
