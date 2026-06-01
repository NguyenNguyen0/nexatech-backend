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

    /** Product catalog, category, brand and review administration. */
    public static final class Product {
        private Product() {
        }

        public static final String READ = "PRODUCT_READ";
        public static final String CREATE = "PRODUCT_CREATE";
        public static final String UPDATE = "PRODUCT_UPDATE";
        public static final String DELETE = "PRODUCT_DELETE";
        public static final String IMAGE_WRITE = "PRODUCT_IMAGE_WRITE";
        public static final String REVIEW_READ = "PRODUCT_REVIEW_READ";
        public static final String REVIEW_MODERATE = "PRODUCT_REVIEW_MODERATE";
    }

    /** Category administration. */
    public static final class Category {
        private Category() {
        }

        public static final String READ = "CATEGORY_READ";
        public static final String WRITE = "CATEGORY_WRITE";
    }

    /** Brand administration. */
    public static final class Brand {
        private Brand() {
        }

        public static final String READ = "BRAND_READ";
        public static final String WRITE = "BRAND_WRITE";
    }

    /** Inventory and warehouse administration. */
    public static final class Inventory {
        private Inventory() {
        }

        public static final String READ = "INVENTORY_READ";
        public static final String WRITE = "INVENTORY_WRITE";
        public static final String IMPORT = "INVENTORY_IMPORT";
    }

    /** News publishing and image asset administration. */
    public static final class News {
        private News() {
        }

        public static final String READ = "NEWS_READ";
        public static final String WRITE = "NEWS_WRITE";
        public static final String IMAGE_WRITE = "NEWS_IMAGE_WRITE";
    }

    /** Notification administration and customer notification preferences. */
    public static final class Notification {
        private Notification() {
        }

        public static final String READ = "NOTIFICATION_READ";
        public static final String WRITE = "NOTIFICATION_WRITE";
        public static final String ADMIN_READ = "NOTIFICATION_ADMIN_READ";
    }

    /** File storage operations. */
    public static final class Storage {
        private Storage() {
        }

        public static final String READ = "STORAGE_READ";
        public static final String WRITE = "STORAGE_WRITE";
    }

    /** Audit log access. */
    public static final class Audit {
        private Audit() {
        }

        public static final String READ = "AUDIT_READ";
        public static final String EXPORT = "AUDIT_EXPORT";
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
                Definition.WRITE,
                Product.READ,
                Product.CREATE,
                Product.UPDATE,
                Product.DELETE,
                Product.IMAGE_WRITE,
                Product.REVIEW_READ,
                Product.REVIEW_MODERATE,
                Category.READ,
                Category.WRITE,
                Brand.READ,
                Brand.WRITE,
                Inventory.READ,
                Inventory.WRITE,
                Inventory.IMPORT,
                News.READ,
                News.WRITE,
                News.IMAGE_WRITE,
                Notification.READ,
                Notification.WRITE,
                Notification.ADMIN_READ,
                Storage.READ,
                Storage.WRITE,
                Audit.READ,
                Audit.EXPORT);
    }
}
