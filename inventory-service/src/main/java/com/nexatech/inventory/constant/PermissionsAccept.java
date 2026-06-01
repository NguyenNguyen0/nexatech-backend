package com.nexatech.inventory.constant;

import java.util.List;

/** Keycloak realm role names that grant access in inventory-service. */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    public static final class Inventory {
        private Inventory() {
        }

        public static final String READ = "INVENTORY_READ";
        public static final String WRITE = "INVENTORY_WRITE";
        public static final String IMPORT = "INVENTORY_IMPORT";
    }

    public static List<String> allCodes() {
        return List.of(Inventory.READ, Inventory.WRITE, Inventory.IMPORT);
    }
}
