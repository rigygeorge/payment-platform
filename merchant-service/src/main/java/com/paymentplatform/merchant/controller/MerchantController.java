package com.paymentplatform.merchant.controller;

import com.paymentplatform.merchant.dto.*;
import com.paymentplatform.merchant.service.MerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Merchant API", description = "Merchant management endpoints")
public class MerchantController {

    private final MerchantService merchantService;

    // API 1 — Register
    @PostMapping("/auth/register")
    @Operation(summary = "Register a new merchant")
    public ResponseEntity<MerchantResponse> register(
            @Valid @RequestBody RegisterMerchantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.register(request));
    }

    // API 2 — Login
    @PostMapping("/auth/login")
    @Operation(summary = "Login and receive JWT token")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(merchantService.login(request));
    }

    // API 3 — Get merchant by ID
    @GetMapping("/merchants/{id}")
    @Operation(summary = "Get merchant by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MerchantResponse> getMerchant(@PathVariable UUID id) {
        return ResponseEntity.ok(merchantService.getMerchantById(id));
    }

    // API 4 — Get all merchants (paginated)
    @GetMapping("/merchants")
    @Operation(summary = "Get all merchants (paginated)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<MerchantResponse>> getAllMerchants(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        return ResponseEntity.ok(merchantService.getAllMerchants(pageable));
    }

    // API 5 — Update merchant
    @PutMapping("/merchants/{id}")
    @Operation(summary = "Update merchant details", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MerchantResponse> updateMerchant(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterMerchantRequest request) {
        return ResponseEntity.ok(merchantService.updateMerchant(id, request));
    }

    // API 6 — Delete (soft) merchant
    @DeleteMapping("/merchants/{id}")
    @Operation(summary = "Deactivate a merchant", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteMerchant(@PathVariable UUID id) {
        merchantService.deleteMerchant(id);
        return ResponseEntity.noContent().build();
    }

    // API 7 — Generate API Key
    @PostMapping("/merchants/{id}/api-key")
    @Operation(summary = "Generate new API key for merchant", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiKeyResponse> generateApiKey(@PathVariable UUID id) {
        return ResponseEntity.ok(merchantService.generateApiKey(id));
    }

    // API 8 — Get balance + stats
    @GetMapping("/merchants/{id}/balance")
    @Operation(summary = "Get merchant balance", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<MerchantResponse> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(merchantService.getMerchantBalance(id));
    }
}