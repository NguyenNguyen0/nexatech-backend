// package com.nexatech.user.config.security;

// import org.keycloak.OAuth2Constants;
// import org.keycloak.admin.client.Keycloak;
// import org.keycloak.admin.client.KeycloakBuilder;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;

// import lombok.AccessLevel;
// import lombok.experimental.FieldDefaults;
// import lombok.experimental.NonFinal;

// @Configuration
// @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
// public class KeycloakAdminConfig {

//     @NonFinal
//     @Value("${keycloak.url}")
//     String KEYCLOAK_URL;
    
//     @NonFinal
//     @Value("${keycloak.realm}")
//     String KEYCLOAK_REALM;

//     @NonFinal
//     @Value("${keycloak.client-id}")
//     String KEYCLOAK_CLIENT_ID;

//     @NonFinal
//     @Value("${keycloak.client-secret}")
//     String KEYCLOAK_CLIENT_SECRET;

//     @NonFinal
//     @Value("${keycloak.user-name}")
//     String KEYCLOAK_USER_NAME;

//     @NonFinal
//     @Value("${keycloak.password}")
//     String KEYCLOAK_PASSWORD;

//     @Bean
//     public Keycloak keycloak() {
//         return KeycloakBuilder
//                 .builder()
//                 .serverUrl(KEYCLOAK_URL)
//                 .realm(KEYCLOAK_REALM)
//                 .clientId(KEYCLOAK_CLIENT_ID)
//                 .clientSecret(KEYCLOAK_CLIENT_SECRET)
//                 .username(KEYCLOAK_USER_NAME)
//                 .password(KEYCLOAK_PASSWORD)
//                 .grantType(OAuth2Constants.PASSWORD)
//                 .build();
//     }
// }
