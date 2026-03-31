package com.paymentplatform.payment.service;

import com.paymentplatform.payment.entity.Payment;
import com.paymentplatform.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rule-based fraud detection engine.
 *
 * Scoring model: each rule adds points to a fraud score (0-100).
 * The final score maps to a FraudResult:
 *   0-39  → APPROVED  (process normally)
 *   40-69 → REVIEW    (process but flag for manual review)
 *   70+   → DECLINED  (reject immediately)
 *
 * Rules implemented:
 *   1. Amount rule    — unusually large single payment
 *   2. Velocity rule  — too many payments in short window
 *   3. Amount sum rule — too much total value in short window
 *
 * Why rule-based and not ML?
 *   For an interview project, rule engines are:
 *   - Explainable (you can tell a merchant exactly why it was flagged)
 *   - Auditable (regulators can inspect the rules)
 *   - Fast (no model inference latency)
 *   ML models sit on top of this in production (Stripe Radar, etc.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final PaymentRepository paymentRepository;

    // -------------------------------------------------------------------------
    // Thresholds — externalise to application.yml in production
    // -------------------------------------------------------------------------
    private static final BigDecimal HIGH_AMOUNT_THRESHOLD       = new BigDecimal("5000");
    private static final BigDecimal VERY_HIGH_AMOUNT_THRESHOLD  = new BigDecimal("10000");
    private static final BigDecimal VELOCITY_AMOUNT_THRESHOLD   = new BigDecimal("20000");

    private static final int VELOCITY_COUNT_WINDOW_MINUTES = 5;
    private static final int VELOCITY_COUNT_THRESHOLD      = 10;
    private static final int VELOCITY_COUNT_HIGH_THRESHOLD = 20;

    // Score weights
    private static final int SCORE_HIGH_AMOUNT       = 20;
    private static final int SCORE_VERY_HIGH_AMOUNT  = 40;
    private static final int SCORE_VELOCITY_COUNT    = 25;
    private static final int SCORE_VELOCITY_HIGH     = 45;
    private static final int SCORE_VELOCITY_AMOUNT   = 30;

    // -------------------------------------------------------------------------
    // Main entry point
    // -------------------------------------------------------------------------

    public FraudCheckResult evaluate(UUID merchantId, BigDecimal amount) {
        int score = 0;
        StringBuilder reasons = new StringBuilder();

        // Rule 1: Single payment amount
        int amountScore = evaluateAmount(amount);
        if (amountScore > 0) {
            score += amountScore;
            reasons.append("HIGH_AMOUNT(").append(amount).append(") ");
        }

        // Rule 2: Payment count velocity
        LocalDateTime windowStart = LocalDateTime.now().minusMinutes(VELOCITY_COUNT_WINDOW_MINUTES);
        long recentCount = paymentRepository.countRecentPayments(merchantId, windowStart);
        int velocityCountScore = evaluateVelocityCount(recentCount);
        if (velocityCountScore > 0) {
            score += velocityCountScore;
            reasons.append("HIGH_VELOCITY_COUNT(").append(recentCount).append(") ");
        }

        // Rule 3: Payment amount velocity
        BigDecimal recentTotal = paymentRepository.sumRecentPaymentAmount(merchantId, windowStart);
        int velocityAmountScore = evaluateVelocityAmount(recentTotal);
        if (velocityAmountScore > 0) {
            score += velocityAmountScore;
            reasons.append("HIGH_VELOCITY_AMOUNT(").append(recentTotal).append(") ");
        }

        // Cap score at 100
        score = Math.min(score, 100);

        Payment.FraudResult result = mapScoreToResult(score);

        log.info("Fraud check — merchantId: {}, amount: {}, score: {}, result: {}, reasons: {}",
                merchantId, amount, score, result, reasons);

        return new FraudCheckResult(score, result, reasons.toString().trim());
    }

    // -------------------------------------------------------------------------
    // Individual rules
    // -------------------------------------------------------------------------

    private int evaluateAmount(BigDecimal amount) {
        if (amount.compareTo(VERY_HIGH_AMOUNT_THRESHOLD) >= 0) {
            return SCORE_VERY_HIGH_AMOUNT;   // $10,000+ → +40 points
        }
        if (amount.compareTo(HIGH_AMOUNT_THRESHOLD) >= 0) {
            return SCORE_HIGH_AMOUNT;        // $5,000+ → +20 points
        }
        return 0;
    }

    private int evaluateVelocityCount(long recentCount) {
        if (recentCount >= VELOCITY_COUNT_HIGH_THRESHOLD) {
            return SCORE_VELOCITY_HIGH;      // 20+ payments in 5 min → +45 points
        }
        if (recentCount >= VELOCITY_COUNT_THRESHOLD) {
            return SCORE_VELOCITY_COUNT;     // 10+ payments in 5 min → +25 points
        }
        return 0;
    }

    private int evaluateVelocityAmount(BigDecimal recentTotal) {
        if (recentTotal.compareTo(VELOCITY_AMOUNT_THRESHOLD) >= 0) {
            return SCORE_VELOCITY_AMOUNT;    // $20,000+ in 5 min → +30 points
        }
        return 0;
    }

    private Payment.FraudResult mapScoreToResult(int score) {
        if (score >= 70) return Payment.FraudResult.DECLINED;
        if (score >= 40) return Payment.FraudResult.REVIEW;
        return Payment.FraudResult.APPROVED;
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    public record FraudCheckResult(
            int score,
            Payment.FraudResult result,
            String reasons
    ) {
        public boolean isDeclined() {
            return result == Payment.FraudResult.DECLINED;
        }

        public boolean isReview() {
            return result == Payment.FraudResult.REVIEW;
        }
    }
}