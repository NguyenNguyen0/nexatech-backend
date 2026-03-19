package com.nexatech.user.repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    boolean existsByKeycloakId(String keycloakId);

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByIdAndActiveTrue(UUID id);

    List<User> findAllByActiveTrue();
}
