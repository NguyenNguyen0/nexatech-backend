package com.nexatech.user.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import com.nexatech.user.model.base.BaseEntity;
import com.nexatech.user.model.constant.Gender;

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
    @Column(length = 64)
    String id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false, unique = true, length = 50)
    String username;

    @Column(length = 50)
    String firstName;

    @Column(length = 50)
    String lastName;

    String avatarUrl;

    @Column(length = 15)
    String phone;

    LocalDate dob;

    @Enumerated(EnumType.STRING)
    Gender gender;

    @Column(name = "on_boarded", nullable = false)
    Boolean onBoarded;
}
