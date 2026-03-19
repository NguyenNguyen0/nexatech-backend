package com.nexatech.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    
}
