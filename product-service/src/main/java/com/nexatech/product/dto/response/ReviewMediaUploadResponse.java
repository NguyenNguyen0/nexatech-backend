package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewMediaUploadResponse {

    private String url;
    private String key;
    private String mediaType;
    private String contentType;
    private long size;
}
