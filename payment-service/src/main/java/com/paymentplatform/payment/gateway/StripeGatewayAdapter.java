package com.paymentplatform.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Mock Stripe gateway adapter.
 *
 * In production this would use the Stripe Java SDK:
 *   Charge.create(params) → real HTTP call to api.stripe.com
 *
 * For this project we simulate realistic behaviour:
 *   - Cards ending in 0000 → decline (insufficient funds)
 *   - Cards ending in 9999 → decline (card blocked)
 *   - Amount > 10,000 → requires manual review (returned as pending)
 *   - Everything else → success
 *
 * Why simulate failures?
 *   So we can test our failure handling, retry logic, and
 *   fraud engine without a real Stripe account.
 */
@Slf4j
@Component
public class StripeGatewayAdapter implements PaymentGatewayAdapter {

    @Override
    public GatewayResponse charge(GatewayRequest request) {
        log.info("Stripe charge request — amount: {} {}, idempotencyKey: {}",
                request.amount(), request.currency(), request.idempotencyKey());

        // Simulate network latency (50-150ms)
        simulateLatency(50, 150);

        // Simulate card-specific decline rules
        if (request.cardLastFour() != null) {
            if (request.cardLastFour().equals("0000")) {
                return GatewayResponse.failure(
                        "insufficient_funds",
                        buildRawResponse("failed", null, "insufficient_funds")
                );
            }
            if (request.cardLastFour().equals("9999")) {
                return GatewayResponse.failure(
                        "card_blocked",
                        buildRawResponse("failed", null, "card_blocked")
                );
            }
        }

        // Simulate large amount going to manual review
        if (request.amount().compareTo(new BigDecimal("10000")) > 0) {
            String ref = generateChargeId();
            log.warn("Large payment {} {} sent for manual review — ref: {}",
                    request.amount(), request.currency(), ref);
            return new GatewayResponse(true, ref, "pending", null,
                    buildRawResponse("pending", ref, null));
        }

        // Success path
        String chargeId = generateChargeId();
        log.info("Stripe charge succeeded — ref: {}", chargeId);
        return GatewayResponse.success(chargeId, buildRawResponse("succeeded", chargeId, null));
    }

    @Override
    public GatewayResponse refund(String gatewayReference, BigDecimal amount) {
        log.info("Stripe refund request — original ref: {}, amount: {}", gatewayReference, amount);

        simulateLatency(40, 100);

        // Simulate refund not found
        if (gatewayReference == null || gatewayReference.isBlank()) {
            return GatewayResponse.failure("charge_not_found",
                    buildRawResponse("failed", null, "charge_not_found"));
        }

        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        log.info("Stripe refund succeeded — refund ref: {}", refundId);
        return GatewayResponse.success(refundId, buildRawResponse("succeeded", refundId, null));
    }

    @Override
    public GatewayResponse getStatus(String gatewayReference) {
        log.info("Stripe status check — ref: {}", gatewayReference);
        simulateLatency(30, 80);

        // Mock: assume succeeded for any valid reference
        return GatewayResponse.success(gatewayReference,
                buildRawResponse("succeeded", gatewayReference, null));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String generateChargeId() {
        // Stripe charge IDs look like: ch_3OqXyz2eZvKYlo2C0abc1234
        return "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    private String buildRawResponse(String status, String id, String failureCode) {
        // Simplified version of what Stripe actually returns
        return String.format("""
                {
                  "id": "%s",
                  "object": "charge",
                  "status": "%s",
                  "failure_code": %s,
                  "created": %d,
                  "livemode": false
                }""",
                id != null ? id : "null",
                status,
                failureCode != null ? "\"" + failureCode + "\"" : "null",
                Instant.now().getEpochSecond()
        );
    }

    private void simulateLatency(int minMs, int maxMs) {
        try {
            int delay = minMs + (int) (Math.random() * (maxMs - minMs));
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}