package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BrandResponse {

    private String id;
    private String name;
    private String slug;
    private String logoUrl;
    private boolean active;
    private Instant createdAt;
}
