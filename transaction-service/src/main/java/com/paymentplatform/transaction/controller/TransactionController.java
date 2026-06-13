package com.paymentplatform.transaction.controller;

import com.paymentplatform.transaction.dto.ReconciliationResponse;
import com.paymentplatform.transaction.dto.TransactionRequest;
import com.paymentplatform.transaction.dto.TransactionResponse;
import com.paymentplatform.transaction.dto.TransactionStatsResponse;
import com.paymentplatform.transaction.entity.TransactionStatus;
import com.paymentplatform.transaction.entity.TransactionType;
import com.paymentplatform.transaction.service.ReconciliationService;
import com.paymentplatform.transaction.service.TransactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction management, reconciliation and export")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;
    private final ReconciliationService reconciliationService;

    // 1. Create transaction
    @PostMapping
    @Operation(summary = "Create a transaction", description = "Idempotent — duplicate referenceId returns the existing record")
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.create(request));
    }

    // 2. Get by ID
    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    // 3. List with filters (paginated)
    @GetMapping
    @Operation(summary = "List transactions for a merchant",
               description = "Filter by type or status. Results sorted newest-first. Uses composite index (merchant_id, created_at).")
    public ResponseEntity<Page<TransactionResponse>> list(
            @RequestParam UUID merchantId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size) {
        return ResponseEntity.ok(transactionService.list(merchantId, type, status, page, size));
    }

    // 4. Stats
    @GetMapping("/stats")
    @Operation(summary = "Aggregated transaction stats for a merchant",
               description = "Returns count, sum, avg broken down by type × status. Useful for merchant dashboard.")
    public ResponseEntity<TransactionStatsResponse> stats(
            @RequestParam UUID merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(transactionService.stats(merchantId, from, to));
    }

    // 5. Reconcile
    @PostMapping("/reconcile")
    @Operation(summary = "Run reconciliation",
               description = "Finds PENDING transactions older than 30 minutes and auto-resolves them. " +
                             "Adyen interview: 'PROCESSING state + reconciliation job = crash-safe payments.'")
    public ResponseEntity<ReconciliationResponse> reconcile() {
        return ResponseEntity.ok(reconciliationService.reconcile());
    }

    // 6. CSV export (StreamingResponseBody — memory efficient for large datasets)
    @GetMapping("/export")
    @Operation(summary = "Export transactions as CSV",
               description = "Uses StreamingResponseBody — writes row-by-row to avoid loading entire result set in memory.")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam UUID merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        String filename = String.format("transactions-%s-%s.csv",
                merchantId, LocalDateTime.now().toLocalDate());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(transactionService.exportCsv(merchantId, from, to));
    }
}