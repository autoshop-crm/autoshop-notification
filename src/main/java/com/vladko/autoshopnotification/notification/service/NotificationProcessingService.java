package com.vladko.autoshopnotification.notification.service;

import java.util.UUID;

import com.vladko.autoshopnotification.config.AppRetryProperties;
import com.vladko.autoshopnotification.email.EmailMessage;
import com.vladko.autoshopnotification.email.EmailSender;
import com.vladko.autoshopnotification.event.dto.EventMetadata;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.notification.entity.InboxStatus;
import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.notification.entity.NotificationDeliveryAttemptEntity;
import com.vladko.autoshopnotification.notification.entity.NotificationEntity;
import com.vladko.autoshopnotification.notification.entity.NotificationEventInboxEntity;
import com.vladko.autoshopnotification.notification.entity.NotificationStatus;
import com.vladko.autoshopnotification.notification.repository.NotificationDeliveryAttemptRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationEventInboxRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationRepository;
import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import com.vladko.autoshopnotification.retry.RetryClassifier;
import com.vladko.autoshopnotification.retry.RetryableNotificationException;
import com.vladko.autoshopnotification.template.service.NotificationTemplateService;
import com.vladko.autoshopnotification.template.service.RenderedNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

@Service
public class NotificationProcessingService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessingService.class);
    private static final int SUPPORTED_VERSION = 1;

    private final NotificationEventInboxRepository inboxRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryAttemptRepository attemptRepository;
    private final NotificationTemplateService templateService;
    private final EmailSender emailSender;
    private final RetryClassifier retryClassifier;
    private final AppRetryProperties retryProperties;

    public NotificationProcessingService(NotificationEventInboxRepository inboxRepository,
                                         NotificationRepository notificationRepository,
                                         NotificationDeliveryAttemptRepository attemptRepository,
                                         NotificationTemplateService templateService,
                                         EmailSender emailSender,
                                         RetryClassifier retryClassifier,
                                         AppRetryProperties retryProperties) {
        this.inboxRepository = inboxRepository;
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.templateService = templateService;
        this.emailSender = emailSender;
        this.retryClassifier = retryClassifier;
        this.retryProperties = retryProperties;
    }

    public void process(NotificationEventEnvelope envelope, EventMetadata metadata) {
        validateEnvelope(envelope);
        NotificationEventInboxEntity inbox = getOrCreateInbox(envelope, metadata);
        if (inbox.getStatus() == InboxStatus.PROCESSED) {
            log.info("Skipping already processed notification event eventId={} eventType={}",
                    envelope.eventId(), envelope.eventType());
            return;
        }

        inbox.markProcessing();
        inboxRepository.save(inbox);

        NotificationEntity notification = null;
        try {
            var existing = notificationRepository.findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL);
            if (existing.isPresent() && existing.get().getStatus() == NotificationStatus.SENT) {
                inbox.markProcessed();
                inboxRepository.save(inbox);
                log.info("Skipping duplicate sent email eventId={} notificationId={}",
                        envelope.eventId(), existing.get().getId());
                return;
            }

            RenderedNotification rendered = templateService.render(envelope);
            notification = existing.orElseGet(() -> createNotification(envelope, rendered));
            notification.markSending();
            notificationRepository.save(notification);

            sendWithRetry(notification, rendered.emailMessage());

            notification.markSent();
            notificationRepository.save(notification);
            inbox.markProcessed();
            inboxRepository.save(inbox);
            log.info("Notification sent eventId={} eventType={} notificationId={} templateKey={}",
                    envelope.eventId(), envelope.eventType(), notification.getId(), notification.getTemplateKey());
        } catch (NonRetryableNotificationException exception) {
            failNonRetryable(inbox, notification, exception);
            throw exception;
        } catch (RetryableNotificationException exception) {
            failRetryable(inbox, notification, exception);
            throw exception;
        } catch (RuntimeException exception) {
            var wrapped = new RetryableNotificationException("Unexpected notification processing failure", exception);
            failRetryable(inbox, notification, wrapped);
            throw wrapped;
        }
    }

    private NotificationEntity createNotification(NotificationEventEnvelope envelope, RenderedNotification rendered) {
        EmailMessage email = rendered.emailMessage();
        try {
            return notificationRepository.save(NotificationEntity.pending(
                    envelope.eventId(),
                    envelope.eventType(),
                    envelope.source(),
                    email.recipient(),
                    email.subject(),
                    rendered.templateKey()
            ));
        } catch (DataIntegrityViolationException exception) {
            return notificationRepository.findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                    .orElseThrow(() -> exception);
        }
    }

    private void sendWithRetry(NotificationEntity notification, EmailMessage emailMessage) {
        AppRetryProperties.Retry retry = retryProperties.email();
        int maxAttempts = Math.max(retry.maxAttempts(), 1);
        MailException lastMailException = null;

        for (int attemptNumber = 1; attemptNumber <= maxAttempts; attemptNumber++) {
            NotificationDeliveryAttemptEntity attempt =
                    attemptRepository.save(NotificationDeliveryAttemptEntity.started(notification, attemptNumber, "SMTP"));
            try {
                emailSender.send(emailMessage);
                attempt.markSuccess();
                attemptRepository.save(attempt);
                return;
            } catch (MailException exception) {
                boolean retryable = retryClassifier.isRetryable(exception);
                attempt.markFailed(retryable, exception.getMessage());
                attemptRepository.save(attempt);
                if (!retryable) {
                    throw new NonRetryableNotificationException("Email sending failed permanently", exception);
                }
                lastMailException = exception;
                if (attemptNumber < maxAttempts) {
                    sleepBeforeRetry(retry.backoff().toMillis());
                }
            }
        }

        throw new RetryableNotificationException("Email sending failed after retry attempts", lastMailException);
    }

    private NotificationEventInboxEntity getOrCreateInbox(NotificationEventEnvelope envelope, EventMetadata metadata) {
        UUID eventId = envelope.eventId();
        return inboxRepository.findById(eventId)
                .orElseGet(() -> {
                    try {
                        return inboxRepository.save(NotificationEventInboxEntity.received(
                                eventId,
                                envelope.eventType(),
                                envelope.source(),
                                metadata.topic(),
                                metadata.partition(),
                                metadata.offset()
                        ));
                    } catch (DataIntegrityViolationException exception) {
                        return inboxRepository.findById(eventId).orElseThrow(() -> exception);
                    }
                });
    }

    private void validateEnvelope(NotificationEventEnvelope envelope) {
        if (envelope == null) {
            throw new NonRetryableNotificationException("Notification event envelope is required");
        }
        if (envelope.eventId() == null) {
            throw new NonRetryableNotificationException("eventId is required");
        }
        if (envelope.eventType() == null || envelope.eventType().isBlank()) {
            throw new NonRetryableNotificationException("eventType is required");
        }
        if (envelope.source() == null || envelope.source().isBlank()) {
            throw new NonRetryableNotificationException("source is required");
        }
        if (envelope.version() == null || envelope.version() != SUPPORTED_VERSION) {
            throw new NonRetryableNotificationException("Unsupported event version: " + envelope.version());
        }
        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new NonRetryableNotificationException("payload is required");
        }
    }

    private void failNonRetryable(NotificationEventInboxEntity inbox,
                                  NotificationEntity notification,
                                  RuntimeException exception) {
        inbox.markFailed(exception.getMessage());
        inboxRepository.save(inbox);
        if (notification != null) {
            notification.markFailed(exception.getMessage());
            notificationRepository.save(notification);
        }
        log.warn("Notification processing failed without retry eventId={} reason={}",
                inbox.getEventId(), exception.getMessage());
    }

    private void failRetryable(NotificationEventInboxEntity inbox,
                               NotificationEntity notification,
                               RuntimeException exception) {
        inbox.markFailed(exception.getMessage());
        inboxRepository.save(inbox);
        if (notification != null) {
            notification.markFailed(exception.getMessage());
            notificationRepository.save(notification);
        }
        log.warn("Notification processing failed and will be retried eventId={} reason={}",
                inbox.getEventId(), exception.getMessage());
    }

    private void sleepBeforeRetry(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableNotificationException("Email retry interrupted", exception);
        }
    }
}
