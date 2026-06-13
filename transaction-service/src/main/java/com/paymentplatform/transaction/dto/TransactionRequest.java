package com.paymentplatform.transaction.dto;

import com.paymentplatform.transaction.entity.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for creating a transaction.
 *
 * referenceId is client-generated — only the client knows when it's retrying.
 * Server-generated keys would cause double charges on retry.
 * (Adyen interview talking point)
 */
public record TransactionRequest(
        @NotNull(message = "merchantId is required")
        UUID merchantId,

        UUID paymentId,                   // optional — FEE rows have no payment

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be > 0")
        BigDecimal amount,

        @NotBlank(message = "currency is required")
        @Size(min = 3, max = 3, message = "currency must be ISO 4217 (3 chars)")
        String currency,

        @NotNull(message = "type is required")
        TransactionType type,

        @NotBlank(message = "referenceId is required")
        @Size(max = 100, message = "referenceId max 100 chars")
        String referenceId,

        String gatewayTransactionId,      // e.g. Stripe ch_xxx
        String metadata                   // arbitrary JSON blob
) {}