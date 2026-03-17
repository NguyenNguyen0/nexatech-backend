package com.nexatech.user.mapper;

import com.nexatech.user.dto.AddressRequest;
import com.nexatech.user.dto.AddressResponse;
import com.nexatech.user.model.Address;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AddressMapper {

    AddressResponse toResponse(Address entity);

    Address toEntity(AddressRequest request);

    void updateEntity(AddressRequest request, @MappingTarget Address entity);
}
