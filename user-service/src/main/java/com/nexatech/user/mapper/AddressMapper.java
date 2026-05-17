package com.nexatech.user.mapper;

import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.model.Address;

public interface AddressMapper {

    Address toEntity(AddressRequest request);

    AddressResponse toResponse(Address address);

    void update(Address address, AddressRequest request);
}
