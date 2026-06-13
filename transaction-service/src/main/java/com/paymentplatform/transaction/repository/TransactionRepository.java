package com.paymentplatform.transaction.repository;

import com.paymentplatform.transaction.entity.Transaction;
import com.paymentplatform.transaction.entity.TransactionStatus;
import com.paymentplatform.transaction.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // ── Basic lookups ──────────────────────────────────────────────────────

    Optional<Transaction> findByReferenceId(String referenceId);

    List<Transaction> findByPaymentId(UUID paymentId);

    // ── Paginated list for a merchant (uses composite index) ───────────────

    Page<Transaction> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId, Pageable pageable);

    Page<Transaction> findByMerchantIdAndTypeOrderByCreatedAtDesc(
            UUID merchantId, TransactionType type, Pageable pageable);

    Page<Transaction> findByMerchantIdAndStatusOrderByCreatedAtDesc(
            UUID merchantId, TransactionStatus status, Pageable pageable);

    // ── Stats query — aggregate per merchant ──────────────────────────────

    @Query("""
            SELECT t.type             AS type,
                   t.status           AS status,
                   COUNT(t)           AS count,
                   SUM(t.amount)      AS totalAmount,
                   AVG(t.amount)      AS avgAmount
            FROM   Transaction t
            WHERE  t.merchantId = :merchantId
              AND  t.createdAt  BETWEEN :from AND :to
            GROUP  BY t.type, t.status
            """)
    List<Object[]> getStatsByMerchant(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Reconciliation — find unreconciled transactions older than cutoff ──

    @Query("""
            SELECT t FROM Transaction t
            WHERE  t.reconciled = false
              AND  t.createdAt  < :cutoff
            ORDER  BY t.createdAt ASC
            """)
    List<Transaction> findUnreconciled(@Param("cutoff") LocalDateTime cutoff);

    // ── Reconciliation mismatch detection ─────────────────────────────────
    // Compares this service's COMPLETED total vs payment-service reported amount

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM   Transaction t
            WHERE  t.merchantId = :merchantId
              AND  t.type       = 'PAYMENT'
              AND  t.status     = 'COMPLETED'
              AND  t.createdAt  BETWEEN :from AND :to
            """)
    BigDecimal sumCompletedPayments(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── CSV export — stream all rows for a merchant in a date range ────────

    @Query("""
            SELECT t FROM Transaction t
            WHERE  t.merchantId = :merchantId
              AND  t.createdAt  BETWEEN :from AND :to
            ORDER  BY t.createdAt ASC
            """)
    List<Transaction> findForExport(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // ── Count helpers ──────────────────────────────────────────────────────

    long countByMerchantId(UUID merchantId);

    long countByMerchantIdAndStatus(UUID merchantId, TransactionStatus status);
}