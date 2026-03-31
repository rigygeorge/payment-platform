package com.paymentplatform.payment.service;

import com.paymentplatform.payment.dto.PaymentRequest;
import com.paymentplatform.payment.dto.PaymentResponse;
import com.paymentplatform.payment.entity.Payment;
import com.paymentplatform.payment.gateway.PaymentGatewayAdapter;
import com.paymentplatform.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository        paymentRepository;
    private final PaymentGatewayAdapter    gatewayAdapter;
    private final IdempotencyService       idempotencyService;
    private final FraudDetectionService    fraudDetectionService;

    // -------------------------------------------------------------------------
    // 1. Initiate payment — main orchestration method
    // -------------------------------------------------------------------------

    @Transactional
    public PaymentResponse initiatePayment(PaymentRequest request) {
        long startTime = System.currentTimeMillis();

        // Step 1 — Idempotency check (Redis fast path)
        var existingId = idempotencyService.getStoredPaymentId(request.getIdempotencyKey());
        if (existingId.isPresent()) {
            log.info("Duplicate payment request — returning cached result for key: {}",
                    request.getIdempotencyKey());
            return paymentRepository.findById(UUID.fromString(existingId.get()))
                    .map(p -> {
                        PaymentResponse response = PaymentResponse.from(p);
                        response.setDuplicate(true);
                        return response;
                    })
                    .orElseThrow(() -> new RuntimeException("Idempotency record found but payment missing"));
        }

        // Step 2 — Fraud detection
        var fraudResult = fraudDetectionService.evaluate(
                request.getMerchantId(), request.getAmount());

        if (fraudResult.isDeclined()) {
            log.warn("Payment declined by fraud engine — merchantId: {}, score: {}, reasons: {}",
                    request.getMerchantId(), fraudResult.score(), fraudResult.reasons());

            // Persist declined payment for audit trail
            Payment declined = buildPayment(request);
            declined.setStatus(Payment.PaymentStatus.FAILED);
            declined.setFraudResult(fraudResult.result());
            declined.setFraudScore(fraudResult.score());
            declined.setFailureReason("FRAUD_DECLINED: " + fraudResult.reasons());
            declined.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            Payment saved = paymentRepository.save(declined);
            idempotencyService.storePaymentId(request.getIdempotencyKey(), saved.getId().toString());
            return PaymentResponse.from(saved);
        }

        // Step 3 — Build and persist PENDING payment
        Payment payment = buildPayment(request);
        payment.setStatus(Payment.PaymentStatus.PROCESSING);
        payment.setFraudResult(fraudResult.result());
        payment.setFraudScore(fraudResult.score());
        payment = paymentRepository.save(payment);

        // Step 4 — Call gateway
        var gatewayRequest = new PaymentGatewayAdapter.GatewayRequest(
                request.getIdempotencyKey(),
                request.getAmount(),
                request.getCurrency(),
                request.getCardToken(),
                request.getCardLastFour(),
                request.getCardBrand(),
                request.getCardholderName(),
                request.getCustomerEmail(),
                request.getDescription()
        );

        var gatewayResponse = gatewayAdapter.charge(gatewayRequest);

        // Step 5 — Update payment with gateway result
        payment.setGatewayReference(gatewayResponse.gatewayReference());
        payment.setGatewayResponse(gatewayResponse.rawResponse());
        payment.setProcessingTimeMs(System.currentTimeMillis() - startTime);

        if (gatewayResponse.success()) {
            payment.setStatus("pending".equals(gatewayResponse.status())
                    ? Payment.PaymentStatus.PENDING
                    : Payment.PaymentStatus.COMPLETED);
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.failureReason());
        }

        payment = paymentRepository.save(payment);

        // Step 6 — Store in Redis idempotency cache
        idempotencyService.storePaymentId(request.getIdempotencyKey(), payment.getId().toString());

        log.info("Payment processed — id: {}, status: {}, processingTime: {}ms",
                payment.getId(), payment.getStatus(), payment.getProcessingTimeMs());

        return PaymentResponse.from(payment);
    }

    // -------------------------------------------------------------------------
    // 2. Get payment by ID
    // -------------------------------------------------------------------------

    public PaymentResponse getPayment(UUID paymentId, UUID merchantId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        // Ensure merchant can only see their own payments
        if (!payment.getMerchantId().equals(merchantId)) {
            throw new RuntimeException("Access denied to payment: " + paymentId);
        }

        return PaymentResponse.from(payment);
    }

    // -------------------------------------------------------------------------
    // 3. List payments (merchant-scoped, paginated)
    // -------------------------------------------------------------------------

    public Page<PaymentResponse> listPayments(UUID merchantId,
                                               Payment.PaymentStatus status,
                                               Pageable pageable) {
        Page<Payment> page = (status != null)
                ? paymentRepository.findByMerchantIdAndStatus(merchantId, status, pageable)
                : paymentRepository.findByMerchantId(merchantId, pageable);

        return page.map(PaymentResponse::from);
    }

    // -------------------------------------------------------------------------
    // 4. Refund
    // -------------------------------------------------------------------------

    @Transactional
    public PaymentResponse refund(UUID paymentId, UUID merchantId) {
        Payment payment = getAndValidate(paymentId, merchantId);

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Only COMPLETED payments can be refunded");
        }

        var gatewayResponse = gatewayAdapter.refund(
                payment.getGatewayReference(), payment.getAmount());

        if (gatewayResponse.success()) {
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            payment.setGatewayReference(gatewayResponse.gatewayReference());
        } else {
            payment.setFailureReason("Refund failed: " + gatewayResponse.failureReason());
        }

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    // -------------------------------------------------------------------------
    // 5. Capture
    // -------------------------------------------------------------------------

    @Transactional
    public PaymentResponse capture(UUID paymentId, UUID merchantId) {
        Payment payment = getAndValidate(paymentId, merchantId);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING) {
            throw new RuntimeException("Only PENDING payments can be captured");
        }

        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    // -------------------------------------------------------------------------
    // 6. Cancel
    // -------------------------------------------------------------------------

    @Transactional
    public PaymentResponse cancel(UUID paymentId, UUID merchantId) {
        Payment payment = getAndValidate(paymentId, merchantId);

        if (payment.getStatus() != Payment.PaymentStatus.PENDING &&
            payment.getStatus() != Payment.PaymentStatus.PROCESSING) {
            throw new RuntimeException("Only PENDING or PROCESSING payments can be cancelled");
        }

        payment.setStatus(Payment.PaymentStatus.CANCELLED);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    // -------------------------------------------------------------------------
    // 7. Stats
    // -------------------------------------------------------------------------

    public PaymentStatsResponse getStats(UUID merchantId,
                                          LocalDateTime from,
                                          LocalDateTime to) {
        var results = paymentRepository.getCompletedStats(merchantId, from, to);

        long totalCount = 0;
        java.math.BigDecimal totalAmount = java.math.BigDecimal.ZERO;

        if (!results.isEmpty()) {
            Object[] row = results.get(0);
            totalCount  = ((Number) row[0]).longValue();
            totalAmount = (java.math.BigDecimal) row[1];
        }

        long allPayments = paymentRepository.countByMerchantId(merchantId);

        return new PaymentStatsResponse(totalCount, totalAmount, allPayments);
    }

    // -------------------------------------------------------------------------
    // 8. Status check (lightweight)
    // -------------------------------------------------------------------------

    public Payment.PaymentStatus getStatus(UUID paymentId, UUID merchantId) {
        return getAndValidate(paymentId, merchantId).getStatus();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Payment buildPayment(PaymentRequest request) {
        return Payment.builder()
                .merchantId(request.getMerchantId())
                .idempotencyKey(request.getIdempotencyKey())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .cardLastFour(request.getCardLastFour())
                .cardBrand(request.getCardBrand())
                .cardholderName(request.getCardholderName())
                .customerEmail(request.getCustomerEmail())
                .customerIp(request.getCustomerIp())
                .description(request.getDescription())
                .build();
    }

    private Payment getAndValidate(UUID paymentId, UUID merchantId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        if (!payment.getMerchantId().equals(merchantId)) {
            throw new RuntimeException("Access denied to payment: " + paymentId);
        }
        return payment;
    }

    // -------------------------------------------------------------------------
    // Stats response record
    // -------------------------------------------------------------------------

    public record PaymentStatsResponse(
            long completedCount,
            java.math.BigDecimal completedAmount,
            long totalPayments
    ) {}
}