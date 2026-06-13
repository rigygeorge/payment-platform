package com.paymentplatform.transaction.service;

import com.paymentplatform.transaction.dto.TransactionRequest;
import com.paymentplatform.transaction.dto.TransactionResponse;
import com.paymentplatform.transaction.dto.TransactionStatsResponse;
import com.paymentplatform.transaction.entity.Transaction;
import com.paymentplatform.transaction.entity.TransactionStatus;
import com.paymentplatform.transaction.entity.TransactionType;
import com.paymentplatform.transaction.exception.TransactionNotFoundException;
import com.paymentplatform.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository repo;

    // ── Create ─────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = "transactions", key = "#request.merchantId()")
    public TransactionResponse create(TransactionRequest request) {
        log.debug("Creating transaction ref={} type={}", request.referenceId(), request.type());

        // Idempotency — same referenceId returns the existing transaction
        return repo.findByReferenceId(request.referenceId())
                .map(this::toResponse)
                .orElseGet(() -> {
                    Transaction tx = Transaction.builder()
                            .merchantId(request.merchantId())
                            .paymentId(request.paymentId())
                            .amount(request.amount())
                            .currency(request.currency().toUpperCase())
                            .type(request.type())
                            .status(TransactionStatus.PENDING)
                            .referenceId(request.referenceId())
                            .gatewayTransactionId(request.gatewayTransactionId())
                            .metadata(request.metadata())
                            .build();
                    return toResponse(repo.save(tx));
                });
    }

    // ── Get by ID ──────────────────────────────────────────────────────────

    @Cacheable(value = "transactions", key = "#id")
    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        return repo.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    // ── List with optional filters ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID merchantId,
                                          TransactionType type,
                                          TransactionStatus status,
                                          int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        if (type != null) {
            return repo.findByMerchantIdAndTypeOrderByCreatedAtDesc(merchantId, type, pageable)
                       .map(this::toResponse);
        }
        if (status != null) {
            return repo.findByMerchantIdAndStatusOrderByCreatedAtDesc(merchantId, status, pageable)
                       .map(this::toResponse);
        }
        return repo.findByMerchantIdOrderByCreatedAtDesc(merchantId, pageable)
                   .map(this::toResponse);
    }

    // ── Stats ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TransactionStatsResponse stats(UUID merchantId, LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = repo.getStatsByMerchant(merchantId, from, to);

        List<TransactionStatsResponse.TypeBreakdown> breakdown = new ArrayList<>();
        long totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        long completedCount = 0;
        long failedCount = 0;

        for (Object[] row : rows) {
            TransactionType  type   = (TransactionType)  row[0];
            TransactionStatus status = (TransactionStatus) row[1];
            long   count      = ((Number) row[2]).longValue();
            BigDecimal total  = (BigDecimal) row[3];
            BigDecimal avg    = (BigDecimal) row[4];

            breakdown.add(new TransactionStatsResponse.TypeBreakdown(type, status, count, total, avg));
            totalCount  += count;
            totalAmount  = totalAmount.add(total);
            if (status == TransactionStatus.COMPLETED) completedCount += count;
            if (status == TransactionStatus.FAILED)    failedCount    += count;
        }

        BigDecimal avgAmount = totalCount > 0
                ? totalAmount.divide(BigDecimal.valueOf(totalCount), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new TransactionStatsResponse(
                merchantId, from, to,
                totalCount, totalAmount, avgAmount,
                completedCount, failedCount, breakdown);
    }

    // ── Update status (used by Kafka consumer in Block 4) ─────────────────

    @Transactional
    @CacheEvict(value = "transactions", key = "#id")
    public TransactionResponse updateStatus(UUID id, TransactionStatus newStatus, String failureReason) {
        Transaction tx = repo.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        tx.setStatus(newStatus);
        if (failureReason != null) tx.setFailureReason(failureReason);
        return toResponse(repo.save(tx));
    }

    // ── CSV export via StreamingResponseBody (memory efficient) ───────────

    public StreamingResponseBody exportCsv(UUID merchantId, LocalDateTime from, LocalDateTime to) {
        List<Transaction> transactions = repo.findForExport(merchantId, from, to);
        log.info("Exporting {} transactions for merchant {}", transactions.size(), merchantId);

        return outputStream -> {
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

                // CSV header
                writer.println("id,merchantId,paymentId,amount,currency,type,status," +
                               "referenceId,gatewayTransactionId,reconciled,createdAt");

                DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                for (Transaction t : transactions) {
                    writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                            t.getId(),
                            t.getMerchantId(),
                            t.getPaymentId() != null ? t.getPaymentId() : "",
                            t.getAmount().toPlainString(),
                            t.getCurrency(),
                            t.getType(),
                            t.getStatus(),
                            t.getReferenceId() != null ? t.getReferenceId() : "",
                            t.getGatewayTransactionId() != null ? t.getGatewayTransactionId() : "",
                            t.isReconciled(),
                            t.getCreatedAt().format(fmt));
                    writer.flush(); // stream row-by-row — no memory buildup
                }
            }
        };
    }

    // ── Mapper ─────────────────────────────────────────────────────────────

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getMerchantId(), t.getPaymentId(),
                t.getAmount(), t.getCurrency(),
                t.getType(), t.getStatus(),
                t.getReferenceId(), t.getGatewayTransactionId(),
                t.getFailureReason(), t.getMetadata(),
                t.isReconciled(), t.getReconciledAt(),
                t.getCreatedAt(), t.getUpdatedAt());
    }
}