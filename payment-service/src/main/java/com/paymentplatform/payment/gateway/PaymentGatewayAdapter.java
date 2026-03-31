package com.paymentplatform.payment.gateway;

import java.math.BigDecimal;

/**
 * Strategy interface for payment gateway integrations.
 *
 * Why an interface?
 * - The PaymentService depends on THIS interface, not on Stripe directly.
 * - Swapping gateways = swap the bean, zero changes to business logic.
 * - In tests, inject a MockGatewayAdapter with no HTTP calls at all.
 *
 * This is the Strategy Pattern — the algorithm (gateway call) is
 * encapsulated behind an interface and swappable at runtime.
 */
public interface PaymentGatewayAdapter {

    /**
     * Charge a payment card.
     *
     * @param request the charge request details
     * @return GatewayResponse with success/failure + gateway reference
     */
    GatewayResponse charge(GatewayRequest request);

    /**
     * Refund a previously completed charge.
     *
     * @param gatewayReference the reference returned from original charge (e.g. Stripe charge ID)
     * @param amount           partial or full refund amount
     * @return GatewayResponse with refund reference
     */
    GatewayResponse refund(String gatewayReference, BigDecimal amount);

    /**
     * Check the status of a charge at the gateway.
     * Used for reconciliation when our local status is uncertain.
     */
    GatewayResponse getStatus(String gatewayReference);

    // -------------------------------------------------------------------------
    // Request / Response value objects (inner records — co-located for clarity)
    // -------------------------------------------------------------------------

    record GatewayRequest(
            String idempotencyKey,
            BigDecimal amount,
            String currency,
            String cardToken,       // tokenised card — never raw card data
            String cardLastFour,
            String cardBrand,
            String cardholderName,
            String customerEmail,
            String description
    ) {}

    record GatewayResponse(
            boolean success,
            String gatewayReference,  // e.g. Stripe charge ID "ch_abc123"
            String status,            // "succeeded", "failed", "pending"
            String failureReason,     // "insufficient_funds", "card_declined" etc
            String rawResponse        // full JSON from gateway (stored for audit)
    ) {
        // Convenience factory methods
        public static GatewayResponse success(String ref, String raw) {
            return new GatewayResponse(true, ref, "succeeded", null, raw);
        }

        public static GatewayResponse failure(String reason, String raw) {
            return new GatewayResponse(false, null, "failed", reason, raw);
        }
    }
}