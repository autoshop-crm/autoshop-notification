package com.vladko.autoshopnotification.event.dto;

import java.time.Instant;

public record OrderCreatedPayload(
        Long orderId,
        String orderNumber,
        Long customerId,
        String customerFirstName,
        String customerLastName,
        String customerEmail,
        Long vehicleId,
        String vehicleBrand,
        String vehicleModel,
        String vehiclePlateNumber,
        Instant createdAt
) {
}
