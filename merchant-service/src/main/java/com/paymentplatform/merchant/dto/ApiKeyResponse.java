package com.paymentplatform.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class ApiKeyResponse {
    private String apiKey;       // full key — shown ONCE only
    private String apiKeyPrefix; // for future display
    private String message;
}