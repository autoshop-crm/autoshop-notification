package com.vladko.autoshopnotification.notification.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_event_channel", columnNames = {"event_id", "channel"}))
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "source", nullable = false, length = 80)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private NotificationStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "provider", length = 60)
    private String provider;

    @Column(name = "provider_message_id", length = 80)
    private String providerMessageId;

    @Column(name = "provider_message_uuid", length = 120)
    private String providerMessageUuid;

    @Column(name = "provider_message_href")
    private String providerMessageHref;

    @Column(name = "provider_accepted_at")
    private Instant providerAcceptedAt;

    protected NotificationEntity() {
    }

    private NotificationEntity(UUID eventId,
                               String eventType,
                               String source,
                               NotificationChannel channel,
                               String recipient,
                               String subject,
                               String templateKey) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.templateKey = templateKey;
        this.status = NotificationStatus.PENDING;
    }

    public static NotificationEntity pending(UUID eventId,
                                             String eventType,
                                             String source,
                                             String recipient,
                                             String subject,
                                             String templateKey) {
        return new NotificationEntity(eventId, eventType, source, NotificationChannel.EMAIL, recipient, subject, templateKey);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void markSending() {
        this.status = NotificationStatus.SENDING;
        this.errorMessage = null;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.errorMessage = null;
    }

    public void markSent(String provider,
                         String providerMessageId,
                         String providerMessageUuid,
                         String providerMessageHref) {
        markSent();
        this.provider = trim(provider, 60);
        this.providerMessageId = trim(providerMessageId, 80);
        this.providerMessageUuid = trim(providerMessageUuid, 120);
        this.providerMessageHref = providerMessageHref;
        this.providerAcceptedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = trimError(errorMessage);
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderMessageId() {
        return providerMessageId;
    }

    public String getProviderMessageUuid() {
        return providerMessageUuid;
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() > 2000 ? message.substring(0, 2000) : message;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
