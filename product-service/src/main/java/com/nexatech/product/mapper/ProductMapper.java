package com.nexatech.product.mapper;

import com.nexatech.product.dto.ProductRequest;
import com.nexatech.product.dto.ProductResponse;
import com.nexatech.product.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductResponse toResponse(Product entity);

    Product toEntity(ProductRequest request);

    void updateEntity(ProductRequest request, @MappingTarget Product entity);
}
