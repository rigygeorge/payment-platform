package com.paymentplatform.transaction.dto;

import com.paymentplatform.transaction.entity.TransactionStatus;
import com.paymentplatform.transaction.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TransactionStatsResponse(
        UUID merchantId,
        LocalDateTime from,
        LocalDateTime to,
        long totalCount,
        BigDecimal totalAmount,
        BigDecimal avgAmount,
        long completedCount,
        long failedCount,
        List<TypeBreakdown> breakdown
) {
    public record TypeBreakdown(
            TransactionType type,
            TransactionStatus status,
            long count,
            BigDecimal totalAmount,
            BigDecimal avgAmount
    ) {}
}