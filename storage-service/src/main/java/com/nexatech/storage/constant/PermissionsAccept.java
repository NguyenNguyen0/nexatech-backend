package com.nexatech.storage.constant;

import java.util.List;

/** Keycloak realm role names reserved for storage-service operations. */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    public static final class Storage {
        private Storage() {
        }

        public static final String READ = "STORAGE_READ";
        public static final String WRITE = "STORAGE_WRITE";
    }

    public static List<String> allCodes() {
        return List.of(Storage.READ, Storage.WRITE);
    }
}
