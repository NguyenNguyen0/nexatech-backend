package com.nexatech.product.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Concrete (non-generic) wrapper for the upload response from storage-service.
 * Used as the Feign client return type to avoid generic type resolution issues
 * with Spring Framework 7 / Spring Cloud OpenFeign 5.x bean introspection.
 */
@Data
@NoArgsConstructor
public class StorageUploadResult {

    private boolean success;
    private StorageFileResponse data;
    private String message;
}
