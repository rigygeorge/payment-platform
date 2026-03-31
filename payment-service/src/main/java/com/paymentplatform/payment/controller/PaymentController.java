package com.paymentplatform.payment.controller;

import com.paymentplatform.payment.dto.PaymentRequest;
import com.paymentplatform.payment.dto.PaymentResponse;
import com.paymentplatform.payment.entity.Payment;
import com.paymentplatform.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing APIs")
public class PaymentController {

    private final PaymentService paymentService;

    // 1. Initiate payment
    @PostMapping
    @Operation(summary = "Initiate a payment")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.initiatePayment(request);

        // Return 200 for duplicate, 201 for new
        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    // 2. Get payment by ID
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId,
            @RequestParam UUID merchantId) {

        return ResponseEntity.ok(paymentService.getPayment(paymentId, merchantId));
    }

    // 3. List payments
    @GetMapping
    @Operation(summary = "List payments for a merchant")
    public ResponseEntity<Page<PaymentResponse>> listPayments(
            @RequestParam UUID merchantId,
            @RequestParam(required = false) Payment.PaymentStatus status,
            Pageable pageable) {

        return ResponseEntity.ok(paymentService.listPayments(merchantId, status, pageable));
    }

    // 4. Refund
    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a completed payment")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID paymentId,
            @RequestParam UUID merchantId) {

        return ResponseEntity.ok(paymentService.refund(paymentId, merchantId));
    }

    // 5. Capture
    @PostMapping("/{paymentId}/capture")
    @Operation(summary = "Capture an authorized payment")
    public ResponseEntity<PaymentResponse> capture(
            @PathVariable UUID paymentId,
            @RequestParam UUID merchantId) {

        return ResponseEntity.ok(paymentService.capture(paymentId, merchantId));
    }

    // 6. Cancel
    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "Cancel a pending payment")
    public ResponseEntity<PaymentResponse> cancel(
            @PathVariable UUID paymentId,
            @RequestParam UUID merchantId) {

        return ResponseEntity.ok(paymentService.cancel(paymentId, merchantId));
    }

    // 7. Stats
    @GetMapping("/stats")
    @Operation(summary = "Get payment statistics for a merchant")
    public ResponseEntity<PaymentService.PaymentStatsResponse> getStats(
            @RequestParam UUID merchantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return ResponseEntity.ok(paymentService.getStats(merchantId, from, to));
    }

    // 8. Status check
    @GetMapping("/{paymentId}/status")
    @Operation(summary = "Get payment status")
    public ResponseEntity<Map<String, String>> getStatus(
            @PathVariable UUID paymentId,
            @RequestParam UUID merchantId) {

        Payment.PaymentStatus status = paymentService.getStatus(paymentId, merchantId);
        return ResponseEntity.ok(Map.of(
                "paymentId", paymentId.toString(),
                "status", status.name()
        ));
    }
}