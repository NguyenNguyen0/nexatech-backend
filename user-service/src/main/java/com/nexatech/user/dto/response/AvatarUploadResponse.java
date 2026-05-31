package com.nexatech.user.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AvatarUploadResponse {

    private final String url;
    private final String key;
    private final String contentType;
    private final long size;
}
