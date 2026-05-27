package com.nexatech.user.model;

import java.util.UUID;

import com.nexatech.user.model.base.BaseEntity;
import com.nexatech.user.model.constant.AddressType;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "addresses")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Address extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    String receiverName;
    String phone;

    String province;
    String district;
    String ward;
    String detail;

    @Column(name = "latitude", nullable = false)
    Double latitude;

    @Column(name = "longitude", nullable = false)
    Double longitude;

    @Enumerated(EnumType.STRING)
    AddressType addressType;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    boolean defaultAddress = false;

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(boolean value) {
        this.defaultAddress = value;
    }
}
