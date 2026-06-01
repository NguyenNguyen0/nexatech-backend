package com.nexatech.news.constant;

import java.util.List;

/** Keycloak realm role names that grant access in news-service. */
public final class PermissionsAccept {

    private PermissionsAccept() {
    }

    public static final class News {
        private News() {
        }

        public static final String READ = "NEWS_READ";
        public static final String WRITE = "NEWS_WRITE";
        public static final String IMAGE_WRITE = "NEWS_IMAGE_WRITE";
    }

    public static List<String> allCodes() {
        return List.of(News.READ, News.WRITE, News.IMAGE_WRITE);
    }
}
