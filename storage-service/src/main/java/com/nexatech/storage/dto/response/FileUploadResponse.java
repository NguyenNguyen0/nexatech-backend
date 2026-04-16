package com.nexatech.storage.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponse {

    /** Public URL to access the file (via Kong gateway). */
    private String url;

    /** S3/MinIO bucket name. */
    private String bucket;

    /** Object key (filename) within the bucket. */
    private String key;

    /** File size in bytes. */
    private long size;

    /** MIME content type (e.g. image/jpeg). */
    private String contentType;
}
