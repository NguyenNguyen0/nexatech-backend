package com.nexatech.chat.config;

import com.mongodb.MongoClientSettings;
import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoUuidConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoUuidRepresentationCustomizer() {
        return (MongoClientSettings.Builder builder) ->
                builder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
