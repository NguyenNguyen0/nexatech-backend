package com.nexatech.user.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, String id);

    boolean existsByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndActiveTrue(String id);

    List<User> findAllByActiveTrue();
}
