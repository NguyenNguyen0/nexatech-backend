package com.nexatech.product.mapper;

import com.nexatech.product.dto.request.CategoryRequest;
import com.nexatech.product.dto.response.CategoryResponse;
import com.nexatech.product.model.Category;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category entity);

    Category toEntity(CategoryRequest request);

    void updateEntity(CategoryRequest request, @MappingTarget Category entity);
}
