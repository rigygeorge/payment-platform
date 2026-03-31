package com.paymentplatform.payment.repository;

import com.paymentplatform.payment.entity.Payment;
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
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByMerchantId(UUID merchantId, Pageable pageable);

    long countByMerchantId(UUID merchantId);

    Page<Payment> findByMerchantIdAndStatus(UUID merchantId, Payment.PaymentStatus status, Pageable pageable);

    // Velocity check: count payments by merchant in last N minutes
    @Query("""
        SELECT COUNT(p) FROM Payment p
        WHERE p.merchantId = :merchantId
        AND p.createdAt >= :since
        AND p.status NOT IN ('FAILED', 'CANCELLED')
        """)
    long countRecentPayments(
            @Param("merchantId") UUID merchantId,
            @Param("since") LocalDateTime since
    );

    // Total amount processed by merchant in last N minutes (velocity by amount)
    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.merchantId = :merchantId
        AND p.createdAt >= :since
        AND p.status NOT IN ('FAILED', 'CANCELLED')
        """)
    BigDecimal sumRecentPaymentAmount(
            @Param("merchantId") UUID merchantId,
            @Param("since") LocalDateTime since
    );

    // Stats query for dashboard
    @Query("""
        SELECT COUNT(p), COALESCE(SUM(p.amount), 0)
        FROM Payment p
        WHERE p.merchantId = :merchantId
        AND p.status = 'COMPLETED'
        AND p.createdAt BETWEEN :from AND :to
        """)
    List<Object[]> getCompletedStats(
            @Param("merchantId") UUID merchantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}