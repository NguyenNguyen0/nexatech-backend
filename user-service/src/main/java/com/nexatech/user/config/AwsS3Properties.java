package com.nexatech.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws.s3")
public class AwsS3Properties {

    private String region = "ap-southeast-1";
    private String bucket;
    private String accessKey;
    private String secretKey;
}
