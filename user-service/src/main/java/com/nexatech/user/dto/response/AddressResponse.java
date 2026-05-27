package com.nexatech.user.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexatech.user.model.constant.AddressType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddressResponse {
    UUID id;
    String userId;
    String receiverName;
    String phone;
    String province;
    String district;
    String ward;
    String detail;
    Double latitude;
    Double longitude;
    AddressType addressType;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    boolean defaultAddress;

    @JsonProperty("isDefault")
    public boolean getIsDefault() {
        return defaultAddress;
    }

    @JsonProperty("isDefault")
    public void setIsDefault(boolean value) {
        this.defaultAddress = value;
    }

    Boolean active;
    Instant createdAt;
    Instant updatedAt;
}
