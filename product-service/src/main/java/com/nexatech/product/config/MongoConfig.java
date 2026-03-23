package com.nexatech.product.config;

import com.nexatech.product.model.Product;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;

@Configuration
@EnableMongoAuditing
public class MongoConfig {

	@Bean
	public ApplicationRunner ensureProductTextIndex(MongoTemplate mongoTemplate) {
		return args -> mongoTemplate.indexOps(Product.class)
				.ensureIndex(new TextIndexDefinition.TextIndexDefinitionBuilder()
						.onField("name", 5F)
						.onField("description", 2F)
						.onField("sku", 3F)
						.build());
	}
}
