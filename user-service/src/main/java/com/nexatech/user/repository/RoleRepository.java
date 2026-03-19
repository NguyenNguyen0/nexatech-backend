package com.nexatech.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.Role;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    
}
