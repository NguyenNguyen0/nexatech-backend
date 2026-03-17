package com.nexatech.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class AddressRequest {

    @NotBlank
    private String recipientName;

    @NotBlank
    private String phone;

    @NotBlank
    private String province;

    @NotBlank
    private String district;

    @NotBlank
    private String ward;

    @NotBlank
    private String streetAddress;

    private boolean defaultAddress;
}
