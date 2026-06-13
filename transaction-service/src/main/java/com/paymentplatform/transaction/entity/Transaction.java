package com.paymentplatform.transaction.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_transactions_merchant_created", columnList = "merchant_id, created_at"),
        @Index(name = "idx_transactions_payment_id",       columnList = "payment_id"),
        @Index(name = "idx_transactions_status",           columnList = "status"),
        @Index(name = "idx_transactions_type",             columnList = "type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // ── Foreign keys ───────────────────────────────────────────────────────
    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "payment_id")          // nullable — FEE rows may have no payment
    private UUID paymentId;

    // ── Money ──────────────────────────────────────────────────────────────
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    // ── Classification ─────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    // ── Reference / audit ─────────────────────────────────────────────────
    @Column(name = "reference_id", unique = true, length = 100)
    private String referenceId;          // idempotency reference from caller

    @Column(name = "gateway_transaction_id", length = 100)
    private String gatewayTransactionId; // e.g. Stripe charge ID

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(columnDefinition = "TEXT")
    private String metadata;             // JSON blob for extra context

    // ── Reconciliation ────────────────────────────────────────────────────
    @Column(name = "reconciled")
    @Builder.Default
    private boolean reconciled = false;

    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    // ── Timestamps ─────────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}