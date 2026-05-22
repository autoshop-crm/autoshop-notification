package com.vladko.autoshopnotification.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_event_inbox")
public class NotificationEventInboxEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "source", nullable = false, length = 80)
    private String source;

    @Column(name = "topic", nullable = false, length = 120)
    private String topic;

    @Column(name = "partition_number")
    private Integer partitionNumber;

    @Column(name = "offset_number")
    private Long offsetNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InboxStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected NotificationEventInboxEntity() {
    }

    private NotificationEventInboxEntity(UUID eventId,
                                         String eventType,
                                         String source,
                                         String topic,
                                         Integer partitionNumber,
                                         Long offsetNumber) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.topic = topic;
        this.partitionNumber = partitionNumber;
        this.offsetNumber = offsetNumber;
        this.status = InboxStatus.RECEIVED;
        this.receivedAt = Instant.now();
    }

    public static NotificationEventInboxEntity received(UUID eventId,
                                                        String eventType,
                                                        String source,
                                                        String topic,
                                                        Integer partitionNumber,
                                                        Long offsetNumber) {
        return new NotificationEventInboxEntity(eventId, eventType, source, topic, partitionNumber, offsetNumber);
    }

    public void markProcessing() {
        this.status = InboxStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markProcessed() {
        this.status = InboxStatus.PROCESSED;
        this.processedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = InboxStatus.FAILED;
        this.errorMessage = trimError(errorMessage);
    }

    public UUID getEventId() {
        return eventId;
    }

    public InboxStatus getStatus() {
        return status;
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
