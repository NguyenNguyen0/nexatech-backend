package com.nexatech.user.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.nexatech.user.model.Address;

@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    Optional<Address> findByIdAndActiveTrue(UUID id);

    Optional<Address> findByIdAndUser_IdAndActiveTrue(UUID id, String userId);

    List<Address> findAllByActiveTrue();

    List<Address> findAllByUser_IdAndActiveTrue(String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE addresses
            SET is_default = false
            WHERE user_id = :userId
              AND (active IS NULL OR active = true)
            """, nativeQuery = true)
    int clearAllDefaultFlags(@Param("userId") String userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE addresses
            SET is_default = true
            WHERE user_id = :userId
              AND id = :addressId
              AND (active IS NULL OR active = true)
            """, nativeQuery = true)
    int markDefault(@Param("userId") String userId, @Param("addressId") UUID addressId);
}
