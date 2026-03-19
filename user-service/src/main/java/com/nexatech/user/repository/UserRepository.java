package com.nexatech.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexatech.user.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    
    boolean existsByKeycloakId(String keycloakId);

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(String keycloakId);
}
