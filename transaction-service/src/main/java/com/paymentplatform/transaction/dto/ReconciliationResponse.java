package com.paymentplatform.transaction.dto;

import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(
        int totalChecked,
        int mismatches,
        int autoFixed,
        List<ReconciliationMismatch> details
) {
    public record ReconciliationMismatch(
            UUID transactionId,
            String issue,
            String action
    ) {}
}