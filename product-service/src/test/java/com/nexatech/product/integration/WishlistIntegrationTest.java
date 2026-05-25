package com.nexatech.product.integration;

import org.bson.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

class WishlistIntegrationTest extends AbstractMongoIntegrationTest {

    private static final String USER_ID = "de305d54-75b4-431b-adb2-eb6b9e546014";
    private static final String ACTIVE_PRODUCT_ID = "wish-prod-active-1";
    private static final String ACTIVE_PRODUCT_ID_2 = "wish-prod-active-2";
    private static final String INACTIVE_PRODUCT_ID = "wish-prod-inactive-1";

    @Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUpData() {
        mongoTemplate.getCollection("wishlists").deleteMany(new Document());
        mongoTemplate.getCollection("reviews").deleteMany(new Document());
        mongoTemplate.getCollection("products").deleteMany(new Document());

        mongoTemplate.getCollection("products").insertOne(baseProduct(ACTIVE_PRODUCT_ID, "Wishlist Active Product 1", true));
        mongoTemplate.getCollection("products").insertOne(baseProduct(ACTIVE_PRODUCT_ID_2, "Wishlist Active Product 2", true));
        mongoTemplate.getCollection("products").insertOne(baseProduct(INACTIVE_PRODUCT_ID, "Wishlist Inactive Product", false));
    }

    @Test
    void addSameProductTwice_shouldBeIdempotentAndStoreSingleRecord() throws Exception {
        HttpResponse<String> first = postWishlist(ACTIVE_PRODUCT_ID, USER_ID);
        HttpResponse<String> second = postWishlist(ACTIVE_PRODUCT_ID, USER_ID);

        Assertions.assertEquals(200, first.statusCode());
        Assertions.assertTrue(first.body().contains("\"wishlisted\":true"));
        Assertions.assertEquals(200, second.statusCode());
        Assertions.assertTrue(second.body().contains("\"wishlisted\":true"));

        long count = mongoTemplate.getCollection("wishlists")
                .countDocuments(new Document("userId", USER_ID).append("productId", ACTIVE_PRODUCT_ID));
        Assertions.assertEquals(1, count);
    }

    @Test
    void removeNonExistingWishlistItem_shouldStillReturnSuccess() throws Exception {
        HttpResponse<String> response = deleteWishlist(ACTIVE_PRODUCT_ID, USER_ID);

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"success\":true"));
        Assertions.assertTrue(response.body().contains("\"wishlisted\":false"));
    }

    @Test
    void getWishlist_shouldReturnPaginatedActiveProductsOnly() throws Exception {
        insertWishlist(USER_ID, ACTIVE_PRODUCT_ID, Instant.parse("2026-05-25T08:00:00Z"));
        insertWishlist(USER_ID, INACTIVE_PRODUCT_ID, Instant.parse("2026-05-25T09:00:00Z"));
        insertWishlist(USER_ID, ACTIVE_PRODUCT_ID_2, Instant.parse("2026-05-25T10:00:00Z"));

        HttpResponse<String> response = getWishlist(USER_ID, "?page=0&size=10");

        Assertions.assertEquals(200, response.statusCode());
        Assertions.assertTrue(response.body().contains("\"success\":true"));
        Assertions.assertTrue(response.body().contains("\"totalElements\":2"));
        Assertions.assertTrue(response.body().contains("\"id\":\"" + ACTIVE_PRODUCT_ID + "\""));
        Assertions.assertTrue(response.body().contains("\"id\":\"" + ACTIVE_PRODUCT_ID_2 + "\""));
        Assertions.assertFalse(response.body().contains("\"id\":\"" + INACTIVE_PRODUCT_ID + "\""));

        int firstIdx = response.body().indexOf("\"id\":\"" + ACTIVE_PRODUCT_ID_2 + "\"");
        int secondIdx = response.body().indexOf("\"id\":\"" + ACTIVE_PRODUCT_ID + "\"");
        Assertions.assertTrue(firstIdx >= 0 && secondIdx > firstIdx);
    }

    @Test
    void addInactiveProduct_shouldReturnNotFound() throws Exception {
        HttpResponse<String> response = postWishlist(INACTIVE_PRODUCT_ID, USER_ID);

        Assertions.assertEquals(404, response.statusCode());
        Assertions.assertTrue(response.body().contains("Product not found"));
    }

    @Test
    void missingUserHeader_shouldReturnBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/products/wishlist/" + ACTIVE_PRODUCT_ID))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assertions.assertEquals(400, response.statusCode());
        Assertions.assertTrue(response.body().contains("X-User-Id"));
        Assertions.assertTrue(response.body().contains("Required request header"));
    }

    private Document baseProduct(String id, String name, boolean active) {
        return new Document()
                .append("_id", id)
                .append("name", name)
                .append("slug", name.toLowerCase().replace(" ", "-"))
                .append("description", "desc")
                .append("price", 100)
                .append("primaryCategoryId", "cat-x")
                .append("categoryIds", java.util.List.of("cat-x"))
                .append("brandId", "brand-x")
                .append("images", java.util.List.of())
                .append("active", active)
                .append("createdAt", Instant.now())
                .append("updatedAt", Instant.now());
    }

    private void insertWishlist(String userId, String productId, Instant createdAt) {
        mongoTemplate.getCollection("wishlists").insertOne(new Document()
                .append("_id", UUID.randomUUID().toString())
                .append("userId", userId)
                .append("productId", productId)
                .append("createdAt", createdAt)
                .append("updatedAt", createdAt));
    }

    private HttpResponse<String> postWishlist(String productId, String userId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/products/wishlist/" + productId))
                .header("X-User-Id", userId)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> deleteWishlist(String productId, String userId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/products/wishlist/" + productId))
                .header("X-User-Id", userId)
                .DELETE()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWishlist(String userId, String query) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/products/wishlist" + query))
                .header("X-User-Id", userId)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
