package com.nexatech.product.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCommandException;
import com.nexatech.product.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

import java.util.List;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Value("${spring.mongodb.uri:}")
    private String mongoUri;

    @Value("${spring.mongodb.username:}")
    private String mongoUsername;

    @Value("${spring.mongodb.database:}")
    private String mongoDatabase;

    @Bean
    public ApplicationRunner logMongoConfiguration() {
        return args -> {
            if (mongoUri == null || mongoUri.isBlank()) {
                log.warn("[MongoConfig] spring.data.mongodb.uri is empty");
                return;
            }

            ConnectionString connectionString = new ConnectionString(mongoUri);
            List<String> hosts = connectionString.getHosts();
            String resolvedDatabase = connectionString.getDatabase();
            String resolvedUsername = connectionString.getUsername() != null
                    ? connectionString.getUsername()
                    : mongoUsername;

            log.info("[MongoConfig] mongodb uri(masked): {}", maskMongoUri(mongoUri));
            log.info("[MongoConfig] mongodb hosts: {}", hosts);
            log.info("[MongoConfig] mongodb username: {}", safeValue(resolvedUsername));
            log.info("[MongoConfig] mongodb database(uri): {}", safeValue(resolvedDatabase));
            log.info("[MongoConfig] mongodb database(property): {}", safeValue(mongoDatabase));
            log.info("[MongoConfig] mongodb connectionString: {}", connectionString);
        };
    }

    @Bean
    public ApplicationRunner ensureProductTextIndex(MongoTemplate mongoTemplate) {
        return args -> {
            try {
                mongoTemplate.indexOps(Product.class)
                        .ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
                                .named("Product_TextIndex")
                                .onField("name", 5F)
                                .onField("description", 2F)
                                .onField("sku", 3F)
                                .build());
            } catch (UncategorizedMongoDbException ex) {
                if (ex.getCause() instanceof MongoCommandException mongoEx && mongoEx.getErrorCode() == 85) {
                    return;
                }
                throw ex;
            }
        };
    }

    private String maskMongoUri(String uri) {
        int protocolSeparator = uri.indexOf("://");
        if (protocolSeparator < 0) {
            return uri;
        }

        int credentialsStart = protocolSeparator + 3;
        int atIndex = uri.indexOf('@', credentialsStart);
        int colonIndex = uri.indexOf(':', credentialsStart);

        if (colonIndex < 0 || atIndex < 0 || colonIndex > atIndex) {
            return uri;
        }

        return uri.substring(0, colonIndex + 1) + "****" + uri.substring(atIndex);
    }

    private String safeValue(String value) {
        return (value == null || value.isBlank()) ? "<empty>" : value;
    }
}
