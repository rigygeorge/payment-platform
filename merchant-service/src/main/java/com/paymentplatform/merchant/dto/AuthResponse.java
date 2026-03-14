package com.paymentplatform.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type = "Bearer";
    private String merchantId;
    private String email;

    public AuthResponse(String token, String merchantId, String email) {
        this.token = token;
        this.merchantId = merchantId;
        this.email = email;
    }
}