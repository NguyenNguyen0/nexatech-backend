package com.nexatech.product.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirrors storage-service FileUploadResponse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageFileResponse {

    private String url;
    private String bucket;
    private String key;
    private long size;
    private String contentType;
}
