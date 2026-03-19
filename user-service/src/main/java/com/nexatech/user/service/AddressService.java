package com.nexatech.user.service;

import java.util.List;

import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;

public interface AddressService {
    AddressResponse create(AddressRequest request);

    List<AddressResponse> getAll();

    List<AddressResponse> getByUserId(String userId);

    AddressResponse getById(String id);

    AddressResponse update(String id, AddressRequest request);

    void delete(String id);
}
