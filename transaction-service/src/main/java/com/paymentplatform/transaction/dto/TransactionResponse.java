package com.paymentplatform.transaction.dto;

import com.paymentplatform.transaction.entity.TransactionStatus;
import com.paymentplatform.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID merchantId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        TransactionType type,
        TransactionStatus status,
        String referenceId,
        String gatewayTransactionId,
        String failureReason,
        String metadata,
        boolean reconciled,
        LocalDateTime reconciledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}