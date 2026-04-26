package com.nexatech.identity.service.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import feign.Response;

public final class KeycloakResponseUtils {

    private KeycloakResponseUtils() {}

    public static String idFromCreatedResourceLocation(Response response, int expectedStatus) {
        if (response.status() != expectedStatus) {
            throw new IllegalStateException(buildUnexpectedStatusMessage(response, expectedStatus));
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

    private static String buildUnexpectedStatusMessage(Response response, int expectedStatus) {
        StringBuilder message = new StringBuilder("Keycloak expected HTTP ")
                .append(expectedStatus)
                .append(", got ")
                .append(response.status());

        String body = readResponseBody(response);
        if (!body.isBlank()) {
            message.append(" | body: ").append(body);
        }
        return message.toString();
    }

    private static String readResponseBody(Response response) {
        if (response.body() == null) {
            return "";
        }
        try (InputStream inputStream = response.body().asInputStream()) {
            byte[] bytes = inputStream.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            return "";
        }
    }
}
