package com.paymentplatform.merchant.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "merchants", indexes = {
    @Index(name = "idx_merchant_email", columnList = "email"),
    @Index(name = "idx_merchant_api_key", columnList = "apiKey"),
    @Index(name = "idx_merchant_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;  // bcrypt hashed

    @Column(name = "api_key", unique = true)
    private String apiKey;

    @Column(name = "api_key_prefix")
    private String apiKeyPrefix;  // first 8 chars for display

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status = MerchantStatus.ACTIVE;

    @Builder.Default
    @Column(name = "balance", precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}