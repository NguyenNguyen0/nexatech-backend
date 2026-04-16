package com.nexatech.product.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageDeleteRequest {

    @NotBlank(message = "imageUrl must not be blank")
    private String imageUrl;
}
