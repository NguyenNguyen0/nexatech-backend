package com.nexatech.user.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.UUID;

import com.nexatech.user.model.base.BaseEntity;
import com.nexatech.user.model.constant.Gender;

@Entity
@Table(name = "users")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity {

    @Column(nullable = false)
    @Id
    UUID id;

    @Column(name = "keycloak_id", unique = true, nullable = false)
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
