package com.nexatech.news.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {

    private String region = "us-east-1";
    private String bucket;
    private String accessKey;
    private String secretKey;
    private String endpoint;
    private String publicUrl;
    private boolean pathStyleAccessEnabled;
}
