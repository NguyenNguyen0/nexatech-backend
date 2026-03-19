package com.nexatech.user.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    
    boolean existsByName(String name);

    Optional<Permission> findByIdAndActiveTrue(UUID id);

    List<Permission> findAllByActiveTrue();
}
