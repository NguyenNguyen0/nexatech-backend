package com.nexatech.user.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
    boolean existsByName(String name);

    Optional<Role> findByIdAndActiveTrue(UUID id);

    List<Role> findAllByActiveTrue();
}
