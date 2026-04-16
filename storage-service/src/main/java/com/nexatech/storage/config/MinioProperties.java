package com.nexatech.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    /** Base URL exposed to clients (via Kong gateway). Used to build file URLs in upload responses. */
    private String publicUrl = "http://localhost:8000";
}
