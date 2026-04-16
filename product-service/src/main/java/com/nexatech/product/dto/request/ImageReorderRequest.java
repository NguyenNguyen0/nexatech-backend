package com.nexatech.product.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ImageReorderRequest {

    @NotEmpty(message = "imageUrls must not be empty")
    private List<String> imageUrls;
}
