package com.nexatech.user.service;

import com.nexatech.user.dto.AddressRequest;
import com.nexatech.user.dto.AddressResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AddressService {

    public List<AddressResponse> getAddresses(UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AddressResponse createAddress(UUID userId, AddressRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AddressResponse updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void deleteAddress(UUID userId, UUID addressId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
