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
    private String region = "ap-southeast-1";
    /** true for MinIO/localstack, false for AWS S3 virtual-hosted style. */
    private boolean pathStyleAccessEnabled = true;
    /** Return direct S3 URL in upload responses instead of gateway URL. */
    private boolean useDirectS3Url = false;
    /** Base URL exposed to clients (via Kong gateway). Used to build file URLs in upload responses. */
    private String publicUrl = "http://localhost:8000";
}
