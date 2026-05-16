package com.nexatech.user.service;

import java.util.List;

import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;

public interface AddressService {
    AddressResponse createAddress(String userId, AddressRequest request);

    List<AddressResponse> findMyAddresses(String userId);

    AddressResponse findMyAddressById(String userId, String id);

    AddressResponse updateMyAddress(String userId, String id, AddressRequest request);

    /** Đặt mặc định và trả về toàn bộ địa chỉ (đã đồng bộ is_default). */
    List<AddressResponse> setDefaultMyAddress(String userId, String id);

    void deleteMyAddress(String userId, String id);
}
