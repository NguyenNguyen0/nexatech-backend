package com.nexatech.user.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.UUID;

import com.nexatech.user.model.base.BaseEntity;
import com.nexatech.user.model.constant.Gender;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    UUID id;

    // Not integrated yet: will be updated later (nullable now).
    // Note: PostgreSQL UNIQUE allows multiple NULL values.
    @Column(name = "keycloak_id", unique = true)
    String keycloakId;

    @Column(nullable = false, unique = true)
    String email;

    @Column(length = 50)
    String fullName;

    String avatarUrl;

    @Column(length = 15)
    String phone;

    LocalDate dob;

    @Enumerated(EnumType.STRING)
    Gender gender;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    Role role;

    @Column(name = "on_boarded", nullable = false)
    Boolean onBoarded;
}
