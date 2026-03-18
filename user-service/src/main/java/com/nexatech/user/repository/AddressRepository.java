package com.nexatech.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexatech.user.model.Address;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    
}
