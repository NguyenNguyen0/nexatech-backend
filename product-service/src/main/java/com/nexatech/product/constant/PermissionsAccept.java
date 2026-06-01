package com.nexatech.product.constant;

import java.util.List;

/** Keycloak realm role names that grant access in product-service. */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

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

    public static final class Category {
        private Category() {
        }

        public static final String READ = "CATEGORY_READ";
        public static final String WRITE = "CATEGORY_WRITE";
    }

    public static final class Brand {
        private Brand() {
        }

        public static final String READ = "BRAND_READ";
        public static final String WRITE = "BRAND_WRITE";
    }

    public static List<String> allCodes() {
        return List.of(
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
                Brand.WRITE);
    }
}
