package com.paymentplatform.payment.exception;

public class DuplicatePaymentException extends RuntimeException {

    private final String idempotencyKey;
    private final String existingPaymentId;

    public DuplicatePaymentException(String idempotencyKey, String existingPaymentId) {
        super(String.format(
            "Payment already processed for idempotency key '%s'. Existing payment: %s",
            idempotencyKey, existingPaymentId
        ));
        this.idempotencyKey = idempotencyKey;
        this.existingPaymentId = existingPaymentId;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getExistingPaymentId() { return existingPaymentId; }
}