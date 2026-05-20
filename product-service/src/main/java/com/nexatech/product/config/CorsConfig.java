package com.nexatech.product.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsConfig {

    // @Bean
    // public WebMvcConfigurer corsConfigurer() {
    //     return new WebMvcConfigurer() {
    //         @Override
    //         public void addCorsMappings(CorsRegistry registry) {
    //             registry.addMapping("/**")
    //                     .allowedOriginPatterns("*")
    //                     .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
    //                     .allowedHeaders("*")
    //                     .allowCredentials(true)
    //                     .maxAge(3600);
    //         }
    //     };
    // }
}
