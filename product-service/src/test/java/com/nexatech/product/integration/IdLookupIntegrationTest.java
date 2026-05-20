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

class IdLookupIntegrationTest extends AbstractMongoIntegrationTest {

    private static final String CATEGORY_ID_PC = "6a0c0d099c7843ff179575b3";
    private static final String CATEGORY_ID_RAM = "69f47283a90b4be8eca1ff8a";
    private static final String CATEGORY_ID_RAM_PARENT = "69f47283a90b4be8eca1ff89";
    private static final String BRAND_ID = "6b1d2e3f4a5b6c7d8e9f0001";
    private static final String PRODUCT_ID = "6c2d3e4f5a6b7c8d9e0f0002";

    @Autowired
        private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

        @LocalServerPort
        private int port;

        private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUpData() {
                mongoTemplate.getCollection("reviews").deleteMany(new Document());
                mongoTemplate.getCollection("products").deleteMany(new Document());
                mongoTemplate.getCollection("brands").deleteMany(new Document());
                mongoTemplate.getCollection("categories").deleteMany(new Document());

        mongoTemplate.getCollection("categories").insertOne(new Document()
                .append("_id", CATEGORY_ID_PC)
                .append("name", "PC")
                .append("slug", "pc")
                .append("description", "")
                .append("parentId", null)
                .append("sortOrder", 0)
                .append("active", true));

        mongoTemplate.getCollection("categories").insertOne(new Document()
                .append("_id", CATEGORY_ID_RAM_PARENT)
                .append("name", "RAM-SSD-HDD")
                .append("slug", "ram-ssd-hdd")
                .append("description", "")
                .append("parentId", null)
                .append("sortOrder", 0)
                .append("active", true));

        mongoTemplate.getCollection("categories").insertOne(new Document()
                .append("_id", CATEGORY_ID_RAM)
                .append("name", "RAM")
                .append("slug", "ram")
                .append("description", "")
                .append("parentId", CATEGORY_ID_RAM_PARENT)
                .append("sortOrder", 0)
                .append("active", true));

        mongoTemplate.getCollection("brands").insertOne(new Document()
                .append("_id", BRAND_ID)
                .append("name", "Corsair")
                .append("slug", "corsair")
                .append("parentBrandId", null)
                .append("logoUrl", null)
                .append("active", true));

        mongoTemplate.getCollection("products").insertOne(new Document()
                .append("_id", PRODUCT_ID)
                .append("name", "Corsair Vengeance 16GB")
                .append("slug", "corsair-vengeance-16gb")
                .append("description", "")
                .append("price", 100)
                .append("primaryCategoryId", CATEGORY_ID_RAM)
                .append("categoryIds", java.util.List.of(CATEGORY_ID_RAM, CATEGORY_ID_RAM_PARENT))
                .append("brandId", BRAND_ID)
                .append("images", java.util.List.of())
                .append("active", true));
    }

    @Test
    void getCategoryById_shouldReturn200() throws Exception {
                HttpResponse<String> response = get("/api/v1/categories/" + CATEGORY_ID_RAM);

                Assertions.assertEquals(200, response.statusCode());
                Assertions.assertTrue(response.body().contains("\"success\":true"));
                Assertions.assertTrue(response.body().contains("\"id\":\"" + CATEGORY_ID_RAM + "\""));
                Assertions.assertTrue(response.body().contains("\"slug\":\"ram\""));
    }

    @Test
    void getProductsByCategorySlug_shouldReturn200() throws Exception {
                HttpResponse<String> response = get("/api/v1/products/category/slug/ram");

                Assertions.assertEquals(200, response.statusCode());
                Assertions.assertTrue(response.body().contains("\"success\":true"));
                Assertions.assertTrue(response.body().contains("\"totalElements\":1"));
                Assertions.assertTrue(response.body().contains("\"id\":\"" + PRODUCT_ID + "\""));
    }

    @Test
    void getBrandById_shouldReturn200() throws Exception {
                HttpResponse<String> response = get("/api/v1/brands/" + BRAND_ID);

                Assertions.assertEquals(200, response.statusCode());
                Assertions.assertTrue(response.body().contains("\"success\":true"));
                Assertions.assertTrue(response.body().contains("\"id\":\"" + BRAND_ID + "\""));
                Assertions.assertTrue(response.body().contains("\"slug\":\"corsair\""));
        }

        private HttpResponse<String> get(String path) throws IOException, InterruptedException {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build();
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
