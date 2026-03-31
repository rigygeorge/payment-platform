package com.paymentplatform.payment.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {

    @NotNull(message = "Merchant ID is required")
    private UUID merchantId;

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 64, message = "Idempotency key must be 64 characters or less")
    private String idempotencyKey;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters (e.g. USD)")
    private String currency;

    // Card details
    @NotBlank(message = "Card token is required")
    private String cardToken;

    @NotBlank(message = "Card last four is required")
    @Size(min = 4, max = 4, message = "Card last four must be exactly 4 digits")
    private String cardLastFour;

    @NotBlank(message = "Card brand is required")
    private String cardBrand;

    @NotBlank(message = "Cardholder name is required")
    private String cardholderName;

    private String customerEmail;
    private String customerIp;
    private String description;
}