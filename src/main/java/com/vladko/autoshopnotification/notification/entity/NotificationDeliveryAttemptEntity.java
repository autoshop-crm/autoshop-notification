package com.vladko.autoshopnotification.notification.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_delivery_attempt")
public class NotificationDeliveryAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", nullable = false)
    private NotificationEntity notification;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private DeliveryAttemptStatus status;

    @Column(name = "provider", nullable = false, length = 60)
    private String provider;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected NotificationDeliveryAttemptEntity() {
    }

    private NotificationDeliveryAttemptEntity(NotificationEntity notification, int attemptNumber, String provider) {
        this.notification = notification;
        this.attemptNumber = attemptNumber;
        this.provider = provider;
        this.status = DeliveryAttemptStatus.STARTED;
        this.startedAt = Instant.now();
    }

    public static NotificationDeliveryAttemptEntity started(NotificationEntity notification, int attemptNumber, String provider) {
        return new NotificationDeliveryAttemptEntity(notification, attemptNumber, provider);
    }

    public void markSuccess() {
        this.status = DeliveryAttemptStatus.SUCCESS;
        this.finishedAt = Instant.now();
        this.errorMessage = null;
    }

    public void markFailed(boolean retryable, String errorMessage) {
        this.status = retryable ? DeliveryAttemptStatus.FAILED_RETRYABLE : DeliveryAttemptStatus.FAILED_NON_RETRYABLE;
        this.finishedAt = Instant.now();
        this.errorMessage = trimError(errorMessage);
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }
}
