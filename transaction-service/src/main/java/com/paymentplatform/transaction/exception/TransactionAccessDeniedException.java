package com.paymentplatform.transaction.exception;

import java.util.UUID;

/**
 * 403 — merchant attempted to access a transaction belonging to a different merchant.
 * Same pattern as PaymentAccessDeniedException in payment-service.
 */
public class TransactionAccessDeniedException extends RuntimeException {
    public TransactionAccessDeniedException(UUID transactionId, UUID merchantId) {
        super("Merchant " + merchantId + " does not have access to transaction " + transactionId);
    }
}