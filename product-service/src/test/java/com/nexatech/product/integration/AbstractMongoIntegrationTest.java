package com.nexatech.product.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractMongoIntegrationTest {

    static final MongoDBContainer MONGO_CONTAINER = new MongoDBContainer("mongo:7.0.14");

    static {
        MONGO_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getReplicaSetUrl);
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("spring.cloud.config.fail-fast", () -> "false");
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:9092");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("CONFIG_SERVER_URI", () -> "http://localhost:8888");
    }
}
