package com.paymentplatform.payment.dto;

import com.paymentplatform.payment.entity.Payment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {

    private UUID id;
    private UUID merchantId;
    private String idempotencyKey;
    private BigDecimal amount;
    private String currency;
    private String cardLastFour;
    private String cardBrand;
    private String cardholderName;
    private Payment.PaymentStatus status;
    private Payment.FraudResult fraudResult;
    private Integer fraudScore;
    private String gatewayReference;
    private String failureReason;
    private String description;
    private String customerEmail;
    private Long processingTimeMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Convenience flag for API consumers
    private boolean duplicate;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .merchantId(payment.getMerchantId())
                .idempotencyKey(payment.getIdempotencyKey())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .cardLastFour(payment.getCardLastFour())
                .cardBrand(payment.getCardBrand())
                .cardholderName(payment.getCardholderName())
                .status(payment.getStatus())
                .fraudResult(payment.getFraudResult())
                .fraudScore(payment.getFraudScore())
                .gatewayReference(payment.getGatewayReference())
                .failureReason(payment.getFailureReason())
                .description(payment.getDescription())
                .customerEmail(payment.getCustomerEmail())
                .processingTimeMs(payment.getProcessingTimeMs())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .duplicate(false)
                .build();
    }
}