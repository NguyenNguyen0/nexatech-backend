package com.nexatech.product.mapper;

import com.nexatech.product.dto.request.BrandRequest;
import com.nexatech.product.dto.response.BrandResponse;
import com.nexatech.product.model.Brand;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface BrandMapper {

    BrandResponse toResponse(Brand entity);

    Brand toEntity(BrandRequest request);

    void updateEntity(BrandRequest request, @MappingTarget Brand entity);
}
