package com.nexatech.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nexatech.user.dto.request.AddressRequest;
import com.nexatech.user.dto.response.AddressResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.AddressMapper;
import com.nexatech.user.model.Address;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.AddressRepository;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.AddressService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AddressServiceImpl implements AddressService {

    AddressRepository addressRepository;
    UserRepository userRepository;
    AddressMapper addressMapper;

    @Override
    public AddressResponse createAddress(String userId, AddressRequest request) {
        if (request == null || userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address address = addressMapper.toEntity(request);
        address.setUser(user);
        if (request.getIsDefault() != null) {
            address.setDefault(request.getIsDefault());
        }

        Address created = addressRepository.save(address);
        if (created.isDefault()) {
            clearOtherDefaultAddresses(userId, created.getId());
        }
        return addressMapper.toResponse(created);
    }

    @Override
    public List<AddressResponse> findMyAddresses(String userId) {
        return addressRepository.findAllByUser_IdAndActiveTrue(userId)
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    public AddressResponse findMyAddressById(String userId, String id) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        return addressMapper.toResponse(address);
    }

    @Override
    public AddressResponse updateMyAddress(String userId, String id, AddressRequest request) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request != null) {
            addressMapper.update(address, request);
            if (request.getIsDefault() != null) {
                address.setDefault(request.getIsDefault());
            }
        }
        Address saved = addressRepository.save(address);
        if (saved.isDefault()) {
            clearOtherDefaultAddresses(userId, saved.getId());
        }
        return addressMapper.toResponse(saved);
    }

    @Override
    public AddressResponse setDefaultMyAddress(String userId, String id) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        address.setDefault(true);
        Address saved = addressRepository.save(address);
        clearOtherDefaultAddresses(userId, saved.getId());
        return addressMapper.toResponse(saved);
    }

    @Override
    public void deleteMyAddress(String userId, String id) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        address.setActive(false);
        addressRepository.save(address);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_UUID);
        }
    }

    private void clearOtherDefaultAddresses(String userId, UUID keepDefaultId) {
        List<Address> addresses = addressRepository.findAllByUser_IdAndActiveTrue(userId);
        boolean changed = false;
        for (Address item : addresses) {
            if (item.isDefault() && !item.getId().equals(keepDefaultId)) {
                item.setDefault(false);
                changed = true;
            }
        }
        if (changed) {
            addressRepository.saveAll(addresses);
        }
    }
}
