package com.paymentplatform.payment.exception;

public class FraudDetectedException extends RuntimeException {

    private final int fraudScore;
    private final String fraudReason;

    public FraudDetectedException(int fraudScore, String fraudReason) {
        super(String.format(
            "Payment declined — fraud score %d/100. Reason: %s",
            fraudScore, fraudReason
        ));
        this.fraudScore = fraudScore;
        this.fraudReason = fraudReason;
    }

    public int getFraudScore() { return fraudScore; }
    public String getFraudReason() { return fraudReason; }
}