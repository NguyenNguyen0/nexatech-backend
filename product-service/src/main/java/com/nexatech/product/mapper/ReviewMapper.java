package com.nexatech.product.mapper;

import com.nexatech.product.dto.request.ReviewRequest;
import com.nexatech.product.dto.response.ReviewResponse;
import com.nexatech.product.model.Review;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    ReviewResponse toResponse(Review entity);

    Review toEntity(ReviewRequest request);
}
