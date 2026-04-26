package com.nexatech.product.client;

import com.nexatech.product.dto.response.StorageUploadResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * Internal Feign client for storage-service.
 * Calls /internal/** endpoints which are NOT exposed through Kong.
 * Resolves via Eureka using application name "storage-service".
 *
 * Return types are concrete non-generic DTOs to avoid Spring Framework 7
 * bean introspection failures with generic types during class loading.
 */
@FeignClient(name = "storage-service")
public interface StorageClient {

    @PostMapping(value = "/internal/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    StorageUploadResult upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String bucket);

    @DeleteMapping("/internal/files")
    void delete(
            @RequestParam String bucket,
            @RequestParam String key);
}
