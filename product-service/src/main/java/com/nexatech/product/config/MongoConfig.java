package com.nexatech.product.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.nexatech.product.model.Product;
import com.nexatech.product.model.Wishlist;
import org.bson.UuidRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Bean
    public MongoClient mongoClient() {
        log.info("[MongoConfig] Creating MongoClient with URI: {}", maskMongoUri(mongoUri));
        ConnectionString connectionString = new ConnectionString(mongoUri);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();
        return MongoClients.create(settings);
    }

    @Bean
    public MongoDatabaseFactory mongoDatabaseFactory(MongoClient mongoClient) {
        ConnectionString connectionString = new ConnectionString(mongoUri);
        String database = connectionString.getDatabase() == null ? "nexatech_products" : connectionString.getDatabase();
        log.info("[MongoConfig] Using database: {}", database);
        return new SimpleMongoClientDatabaseFactory(mongoClient, database);
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTemplate(mongoDatabaseFactory);
    }

    @Bean
    public ApplicationRunner ensureProductTextIndex(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                log.info("[MongoConfig] Ensuring indexes for Product collection...");
                mongoTemplate.indexOps(Product.class).createIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
                        .named("Product_TextIndex")
                        .onField("name", 5F)
                        .onField("description", 2F)
                        .onField("sku", 3F)
                        .build());
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("active", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.DESC)
                                .named("product_active_updated_desc")
                );
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("active", Sort.Direction.ASC)
                                .on("categoryIds", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.DESC)
                                .named("product_active_category_updated_desc")
                );
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("active", Sort.Direction.ASC)
                                .on("categoryIds", Sort.Direction.ASC)
                                .on("price", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.DESC)
                                .named("product_active_category_price_updated_desc")
                );
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("active", Sort.Direction.ASC)
                                .on("price", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.DESC)
                                .named("product_active_price_updated_desc")
                );
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("active", Sort.Direction.ASC)
                                .on("brandId", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.DESC)
                                .named("product_active_brand_updated_desc")
                );
                mongoTemplate.indexOps(Product.class).createIndex(
                        new Index()
                                .on("slug", Sort.Direction.ASC)
                                .named("product_slug")
                                .unique()
                );
                log.info("[MongoConfig] Product indexes ensured successfully.");
            } catch (Exception ex) {
                log.error("[MongoConfig] Failed to ensure product indexes: {}", ex.getMessage());
            }

            try {
                log.info("[MongoConfig] Ensuring indexes for Wishlist collection...");
                mongoTemplate.indexOps(Wishlist.class).createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("productId", Sort.Direction.ASC)
                                .named("wishlist_user_product_unique")
                                .unique()
                );
                mongoTemplate.indexOps(Wishlist.class).createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)
                                .named("wishlist_user_created_desc")
                );
                log.info("[MongoConfig] Wishlist indexes ensured successfully.");
            } catch (Exception ex) {
                log.error("[MongoConfig] Failed to ensure wishlist indexes: {}", ex.getMessage());
            }
        };
    }

    private String maskMongoUri(String uri) {
        try {
            int protocolSeparator = uri.indexOf("://");
            if (protocolSeparator < 0) return uri;
            int credentialsStart = protocolSeparator + 3;
            int atIndex = uri.indexOf('@', credentialsStart);
            int colonIndex = uri.indexOf(':', credentialsStart);
            if (colonIndex < 0 || atIndex < 0 || colonIndex > atIndex) return uri;
            return uri.substring(0, colonIndex + 1) + "****" + uri.substring(atIndex);
        } catch (Exception e) {
            return "invalid-uri";
        }
    }
}
