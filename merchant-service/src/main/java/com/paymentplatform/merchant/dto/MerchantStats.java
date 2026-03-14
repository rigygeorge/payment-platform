package com.paymentplatform.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data @AllArgsConstructor
public class MerchantStats {
    private long totalMerchants;
    private long activeMerchants;
}