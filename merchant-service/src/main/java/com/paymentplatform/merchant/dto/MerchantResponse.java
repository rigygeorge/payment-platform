package com.paymentplatform.merchant.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.paymentplatform.merchant.entity.MerchantStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
public class MerchantResponse {
    private UUID id;
    private String name;
    private String email;
    private String businessName;
    private String apiKeyPrefix;   // never return full key
    private MerchantStatus status;
    private BigDecimal balance;
    private String webhookUrl;
    private String countryCode;
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;
}