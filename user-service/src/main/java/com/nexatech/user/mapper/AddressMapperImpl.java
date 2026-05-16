package com.nexatech.user.mapper;

import org.springframework.stereotype.Component;

import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.model.Address;

@Component
public class AddressMapperImpl implements AddressMapper {

    @Override
    public Address toEntity(AddressRequest request) {
        if (request == null) {
            return null;
        }
        return Address.builder()
                .receiverName(request.getReceiverName())
                .phone(request.getPhone())
                .province(request.getProvince())
                .ward(request.getWard())
                .detail(request.getDetail())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .addressType(request.getAddressType())
                .defaultAddress(Boolean.TRUE.equals(request.getIsDefault()))
                .build();
    }

    @Override
    public AddressResponse toResponse(Address address) {
        if (address == null) {
            return null;
        }
        AddressResponse response = AddressResponse.builder()
                .id(address.getId())
                .userId(address.getUser() != null ? address.getUser().getId() : null)
                .receiverName(address.getReceiverName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .ward(address.getWard())
                .detail(address.getDetail())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .addressType(address.getAddressType())
                .active(address.getActive())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
        response.setIsDefault(address.isDefaultAddress());
        return response;
    }

    @Override
    public void update(Address address, AddressRequest request) {
        if (address == null || request == null) {
            return;
        }
        if (request.getReceiverName() != null) {
            address.setReceiverName(request.getReceiverName());
        }
        if (request.getPhone() != null) {
            address.setPhone(request.getPhone());
        }
        if (request.getProvince() != null) {
            address.setProvince(request.getProvince());
        }
        if (request.getWard() != null) {
            address.setWard(request.getWard());
        }
        if (request.getDetail() != null) {
            address.setDetail(request.getDetail());
        }
        if (request.getLatitude() != null) {
            address.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            address.setLongitude(request.getLongitude());
        }
        if (request.getAddressType() != null) {
            address.setAddressType(request.getAddressType());
        }
    }
}
