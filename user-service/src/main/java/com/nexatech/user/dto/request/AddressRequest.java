package com.nexatech.user.dto.request;

import com.nexatech.user.model.constant.AddressType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
    /** Optional; create flow uses authenticated user id. */
    String userId;

    @NotBlank(message = "RECEIVER_NAME_REQUIRED")
    String receiverName;

    @NotBlank(message = "PHONE_REQUIRED")
    String phone;

    @NotBlank(message = "PROVINCE_REQUIRED")
    String province;

    String ward;

    @NotBlank(message = "DETAIL_REQUIRED")
    String detail;
    Double latitude;
    Double longitude;
    @NotNull(message = "ADDRESS_TYPE_REQUIRED")
    AddressType addressType;

    Boolean isDefault;
}

