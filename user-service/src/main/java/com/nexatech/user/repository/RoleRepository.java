package com.nexatech.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexatech.user.model.Role;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    
}
