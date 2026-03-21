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
    public AddressResponse create(AddressRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        User user = userRepository.findByIdAndActiveTrue(request.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address address = addressMapper.toEntity(request);
        address.setUser(user);
        if (request.getIsDefault() != null) {
            address.setDefault(request.getIsDefault());
        }

        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    public List<AddressResponse> getAll() {
        return addressRepository.findAllByActiveTrue()
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    public List<AddressResponse> getByUserId(String userId) {
        return addressRepository.findAllByUser_IdAndActiveTrue(userId)
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    public AddressResponse getById(String id) {
        Address address = addressRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        return addressMapper.toResponse(address);
    }

    @Override
    public AddressResponse update(String id, AddressRequest request) {
        Address address = addressRepository.findByIdAndActiveTrue(parseUuid(id))
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));

        if (request != null && request.getUserId() != null) {
            User user = userRepository.findByIdAndActiveTrue(request.getUserId())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
            address.setUser(user);
        }

        if (request != null) {
            addressMapper.update(address, request);
            if (request.getIsDefault() != null) {
                address.setDefault(request.getIsDefault());
            }
        }
        return addressMapper.toResponse(addressRepository.save(address));
    }

    @Override
    public void delete(String id) {
        Address address = addressRepository.findByIdAndActiveTrue(parseUuid(id))
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
}
