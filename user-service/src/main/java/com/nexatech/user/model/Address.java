package com.nexatech.user.model;

import java.util.UUID;

import com.nexatech.user.model.base.BaseEntity;
import com.nexatech.user.model.constant.AddressType;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Entity
@Table(name = "addresses")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends BaseEntity {

    @Column(nullable = false)
    UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    String receiverName;
    String phone;

    String province; // Tỉnh/Thành phố
    String ward;     // Phường/Xã
    String detail;   // Số nhà, tên đường

    @Column(name = "latitude", nullable = false)
    Double latitude;

    @Column(name = "longitude", nullable = false)
    Double longitude;

    @Enumerated(EnumType.STRING)
    AddressType addressType;

    @Column(name = "is_default", nullable = false)
    boolean isDefault = false;

}
