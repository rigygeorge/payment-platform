package com.paymentplatform.merchant.service;

import com.paymentplatform.merchant.dto.*;
import com.paymentplatform.merchant.entity.Merchant;
import com.paymentplatform.merchant.entity.MerchantStatus;
import com.paymentplatform.merchant.exception.MerchantNotFoundException;
import com.paymentplatform.merchant.exception.DuplicateEmailException;
import com.paymentplatform.merchant.repository.MerchantRepository;
import com.paymentplatform.merchant.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public MerchantResponse register(RegisterMerchantRequest request) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email already registered: " + request.getEmail());
        }

        Merchant merchant = Merchant.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .businessName(request.getBusinessName())
                .webhookUrl(request.getWebhookUrl())
                .countryCode(request.getCountryCode())
                .status(MerchantStatus.ACTIVE)
                .balance(BigDecimal.ZERO)
                .build();

        Merchant saved = merchantRepository.save(merchant);
        log.info("Registered new merchant: {}", saved.getId());
        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Merchant merchant = merchantRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found"));

        String token = jwtUtil.generateToken(merchant.getEmail(), merchant.getId().toString());
        log.info("Merchant logged in: {}", merchant.getId());
        return new AuthResponse(token, merchant.getId().toString(), merchant.getEmail());
    }

    @Cacheable(value = "merchants", key = "#id")
    public MerchantResponse getMerchantById(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + id));
        return toResponse(merchant);
    }

    @Cacheable(value = "merchants", key = "'all_' + #pageable.pageNumber")
    public Page<MerchantResponse> getAllMerchants(Pageable pageable) {
        return merchantRepository.findAll(pageable).map(this::toResponse);
    }

    @CacheEvict(value = "merchants", key = "#id")
    @Transactional
    public MerchantResponse updateMerchant(UUID id, RegisterMerchantRequest request) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + id));

        merchant.setName(request.getName());
        merchant.setBusinessName(request.getBusinessName());
        merchant.setWebhookUrl(request.getWebhookUrl());
        merchant.setCountryCode(request.getCountryCode());

        return toResponse(merchantRepository.save(merchant));
    }

    @CacheEvict(value = "merchants", key = "#id")
    @Transactional
    public void deleteMerchant(UUID id) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + id));
        merchant.setStatus(MerchantStatus.INACTIVE);  // soft delete
        merchantRepository.save(merchant);
        log.info("Soft-deleted merchant: {}", id);
    }

    @Transactional
    public ApiKeyResponse generateApiKey(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + merchantId));

        String rawApiKey = "pk_" + generateSecureKey();
        merchant.setApiKey(passwordEncoder.encode(rawApiKey));  // store hashed
        merchant.setApiKeyPrefix(rawApiKey.substring(0, 10));   // pk_XXXXXXXX
        merchantRepository.save(merchant);

        log.info("Generated new API key for merchant: {}", merchantId);
        // Return raw key ONCE — never stored in plain text
        return new ApiKeyResponse(rawApiKey, merchant.getApiKeyPrefix(),
                "Store this key securely — it will not be shown again");
    }

    public MerchantResponse getMerchantBalance(UUID id) {
        return getMerchantById(id);  // balance is part of response
    }

    @Transactional
    public MerchantResponse updateBalance(UUID id, BigDecimal amount) {
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException("Merchant not found: " + id));
        merchant.setBalance(merchant.getBalance().add(amount));
        return toResponse(merchantRepository.save(merchant));
    }

    public MerchantStats getMerchantStats() {
        long total = merchantRepository.count();
        long active = merchantRepository.countByStatus(MerchantStatus.ACTIVE);
        return new MerchantStats(total, active);
    }

    private String generateSecureKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private MerchantResponse toResponse(Merchant m) {
        return MerchantResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .email(m.getEmail())
                .businessName(m.getBusinessName())
                .apiKeyPrefix(m.getApiKeyPrefix())
                .status(m.getStatus())
                .balance(m.getBalance())
                .webhookUrl(m.getWebhookUrl())
                .countryCode(m.getCountryCode())
                .createdAt(m.getCreatedAt())
                .build();
    }
}