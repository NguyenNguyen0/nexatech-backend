package com.nexatech.user.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import jakarta.persistence.EntityManager;
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
    EntityManager entityManager;

    @Override
    @Transactional
    public AddressResponse createAddress(String userId, AddressRequest request) {
        if (request == null || userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address address = addressMapper.toEntity(request);
        address.setUser(user);
        address.setDefaultAddress(false);

        Address created = addressRepository.save(address);
        if (Boolean.TRUE.equals(request.getIsDefault()) || !hasDefaultAddress(userId)) {
            applySingleDefault(userId, created.getId());
        }
        return reloadAddressResponse(userId, created.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> findMyAddresses(String userId) {
        return addressRepository.findAllByUser_IdAndActiveTrue(userId)
                .stream()
                .map(addressMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse findMyAddressById(String userId, String id) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        return addressMapper.toResponse(address);
    }

    @Override
    @Transactional
    public AddressResponse updateMyAddress(String userId, String id, AddressRequest request) {
        UUID addressId = parseUuid(id);
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(addressId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request != null) {
            addressMapper.update(address, request);
            addressRepository.save(address);

            if (Boolean.TRUE.equals(request.getIsDefault())) {
                applySingleDefault(userId, addressId);
            } else if (Boolean.FALSE.equals(request.getIsDefault())) {
                clearAllDefaultsForUser(userId);
            }
        }
        return reloadAddressResponse(userId, addressId);
    }

    @Override
    @Transactional
    public List<AddressResponse> setDefaultMyAddress(String userId, String id) {
        UUID addressId = parseUuid(id);
        addressRepository.findByIdAndUser_IdAndActiveTrue(addressId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        applySingleDefault(userId, addressId);
        entityManager.clear();
        return findMyAddresses(userId);
    }

    @Override
    @Transactional
    public void deleteMyAddress(String userId, String id) {
        Address address = addressRepository.findByIdAndUser_IdAndActiveTrue(parseUuid(id), userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        boolean wasDefault = address.isDefaultAddress();
        address.setActive(false);
        address.setDefaultAddress(false);
        addressRepository.save(address);
        if (wasDefault) {
            promoteFirstRemainingAsDefault(userId);
        }
    }

    private void applySingleDefault(String userId, UUID defaultAddressId) {
        addressRepository.clearAllDefaultFlags(userId);
        int updated = addressRepository.markDefault(userId, defaultAddressId);
        if (updated == 0) {
            throw new AppException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void clearAllDefaultsForUser(String userId) {
        addressRepository.clearAllDefaultFlags(userId);
        entityManager.flush();
        entityManager.clear();
    }

    private boolean hasDefaultAddress(String userId) {
        return addressRepository.findAllByUser_IdAndActiveTrue(userId).stream()
                .anyMatch(Address::isDefaultAddress);
    }

    private void promoteFirstRemainingAsDefault(String userId) {
        List<Address> remaining = addressRepository.findAllByUser_IdAndActiveTrue(userId);
        if (remaining.isEmpty()) {
            return;
        }
        applySingleDefault(userId, remaining.getFirst().getId());
    }

    private AddressResponse reloadAddressResponse(String userId, UUID addressId) {
        entityManager.clear();
        Address fresh = addressRepository.findByIdAndUser_IdAndActiveTrue(addressId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
        return addressMapper.toResponse(fresh);
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_UUID);
        }
    }
}
