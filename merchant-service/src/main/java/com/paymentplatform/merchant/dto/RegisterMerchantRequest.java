package com.paymentplatform.merchant.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterMerchantRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Valid email required")
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    private String businessName;
    private String webhookUrl;

    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String countryCode;
}