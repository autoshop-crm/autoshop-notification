package com.vladko.autoshopnotification.event.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record NotificationEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String source,
        Integer version,
        String correlationId,
        JsonNode payload
) {
}
