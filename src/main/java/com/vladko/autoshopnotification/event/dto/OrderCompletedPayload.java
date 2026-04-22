package com.vladko.autoshopnotification.event.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCompletedPayload(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        Instant completedAt,
        BigDecimal finalAmount,
        String currency,
        Integer loyaltyPointsEarned
) {
}
