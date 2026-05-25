package com.nexatech.product.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WishlistActionResponse {

    private String productId;
    private boolean wishlisted;
}
