package com.nexatech.user.dto.request;

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
public class AddressRequest {
    String userId;
    String receiverName;
    String phone;
    String province;
    String ward;
    String detail;
    Double latitude;
    Double longitude;
    AddressType addressType;
    Boolean isDefault;
}

