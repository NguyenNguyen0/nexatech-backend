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

class InternalIntegrityEndpointTest extends AbstractMongoIntegrationTest {

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
                .append("_id", "cat-1")
                .append("name", "Category 1")
                .append("slug", "category-1")
                .append("parentId", null)
                .append("sortOrder", 0)
                .append("active", true));

        mongoTemplate.getCollection("brands").insertOne(new Document()
                .append("_id", "brand-1")
                .append("name", "Brand 1")
                .append("slug", "brand-1")
                .append("parentBrandId", null)
                .append("active", true));

        mongoTemplate.getCollection("products").insertOne(new Document()
                .append("_id", "prod-1")
                .append("name", "Product 1")
                .append("slug", "product-1")
                .append("primaryCategoryId", "cat-1")
                .append("categoryIds", java.util.List.of("cat-1"))
                .append("brandId", "brand-1")
                .append("active", true));

        mongoTemplate.getCollection("reviews").insertOne(new Document()
                .append("_id", "review-1")
                .append("productId", "prod-1")
                .append("rating", 5));
    }

    @Test
    void idReferenceIntegrityEndpoint_shouldReturnHealthy_whenReferencesAreValid() throws Exception {
                HttpResponse<String> response = get("/api/v1/internal/integrity/id-references");

                Assertions.assertEquals(200, response.statusCode());
                Assertions.assertTrue(response.body().contains("\"success\":true"));
                Assertions.assertTrue(response.body().contains("\"healthy\":true"));
                Assertions.assertTrue(response.body().contains("\"orphanCategoryParentRefs\":0"));
                Assertions.assertTrue(response.body().contains("\"orphanProductPrimaryCategoryRefs\":0"));
                Assertions.assertTrue(response.body().contains("\"orphanProductBrandRefs\":0"));
                Assertions.assertTrue(response.body().contains("\"orphanReviewProductRefs\":0"));
        }

        private HttpResponse<String> get(String path) throws IOException, InterruptedException {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + path))
                                .GET()
                                .build();
                return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
