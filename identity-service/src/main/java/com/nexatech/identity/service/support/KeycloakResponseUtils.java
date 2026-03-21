package com.nexatech.identity.service.support;

import java.net.URI;
import java.util.Collection;

import feign.Response;

public final class KeycloakResponseUtils {

    private KeycloakResponseUtils() {}

    public static String idFromCreatedResourceLocation(Response response, int expectedStatus) {
        if (response.status() != expectedStatus) {
            throw new IllegalStateException("Keycloak expected HTTP " + expectedStatus + ", got " + response.status());
        }
        Collection<String> locations = response.headers().get("Location");
        if (locations == null || locations.isEmpty()) {
            throw new IllegalStateException("Keycloak response missing Location header");
        }
        String location = locations.iterator().next();
        String path = location;
        try {
            if (location.contains("://")) {
                path = URI.create(location).getPath();
            }
        } catch (IllegalArgumentException ignored) {
            // keep path as location
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash >= path.length() - 1) {
            throw new IllegalStateException("Invalid Location header: " + location);
        }
        return path.substring(lastSlash + 1);
    }
}
