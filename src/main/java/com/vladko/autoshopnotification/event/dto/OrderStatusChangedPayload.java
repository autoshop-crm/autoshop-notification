package com.vladko.autoshopnotification.event.dto;

import java.time.Instant;

public record OrderStatusChangedPayload(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        String previousStatus,
        String newStatus,
        Instant changedAt,
        String managerComment
) {
}
