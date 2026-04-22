package com.vladko.autoshopnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladko.autoshopnotification.email.EmailMessage;
import com.vladko.autoshopnotification.email.EmailSendResult;
import com.vladko.autoshopnotification.email.EmailSender;
import com.vladko.autoshopnotification.event.dto.EventMetadata;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.event.dto.OrderCreatedPayload;
import com.vladko.autoshopnotification.notification.entity.InboxStatus;
import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.notification.entity.NotificationStatus;
import com.vladko.autoshopnotification.notification.repository.NotificationDeliveryAttemptRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationEventInboxRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationRepository;
import com.vladko.autoshopnotification.notification.service.NotificationProcessingService;
import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class NotificationProcessingServiceTest {

    private static final EventMetadata METADATA = new EventMetadata("autoshop.order-events", 0, 1L);

    @Autowired
    private NotificationProcessingService processingService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventInboxRepository inboxRepository;

    @Autowired
    private NotificationDeliveryAttemptRepository attemptRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void cleanDatabase() {
        attemptRepository.deleteAll();
        notificationRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void sendsEmailAndMarksEventProcessed() {
        NotificationEventEnvelope envelope = orderCreatedEnvelope(UUID.randomUUID(), "ivan@example.com");

        processingService.process(envelope, METADATA);

        var notification = notificationRepository
                .findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getTemplateKey()).isEqualTo("ORDER_CREATED_EMAIL");
        assertThat(notification.getRecipient()).isEqualTo("ivan@example.com");
        assertThat(inboxRepository.findById(envelope.eventId()).orElseThrow().getStatus()).isEqualTo(InboxStatus.PROCESSED);
        assertThat(attemptRepository.count()).isEqualTo(1);
        verify(emailSender, times(1)).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));
    }

    @Test
    void skipsDuplicateProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        NotificationEventEnvelope envelope = orderCreatedEnvelope(eventId, "ivan@example.com");

        processingService.process(envelope, METADATA);
        processingService.process(envelope, METADATA);

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(attemptRepository.count()).isEqualTo(1);
        verify(emailSender, times(1)).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));
    }

    @Test
    void retriesTemporaryEmailFailure() {
        NotificationEventEnvelope envelope = orderCreatedEnvelope(UUID.randomUUID(), "ivan@example.com");
        doThrow(new MailSendException("smtp temporary failure"))
                .doReturn(EmailSendResult.accepted("SMTP"))
                .when(emailSender).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));

        processingService.process(envelope, METADATA);

        assertThat(notificationRepository.findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(attemptRepository.count()).isEqualTo(2);
        verify(emailSender, times(2)).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));
    }

    @Test
    void storesProviderMetadataFromEmailSenderResult() {
        NotificationEventEnvelope envelope = orderCreatedEnvelope(UUID.randomUUID(), "ivan@example.com");
        when(emailSender.providerName()).thenReturn("MAILJET");
        when(emailSender.send(org.mockito.ArgumentMatchers.any(EmailMessage.class)))
                .thenReturn(new EmailSendResult("MAILJET", "123456789", "uuid-123", "https://api.mailjet.com/v3/message/123456789"));

        processingService.process(envelope, METADATA);

        var notification = notificationRepository
                .findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow();
        assertThat(notification.getProvider()).isEqualTo("MAILJET");
        assertThat(notification.getProviderMessageId()).isEqualTo("123456789");
        assertThat(notification.getProviderMessageUuid()).isEqualTo("uuid-123");
        assertThat(attemptRepository.findAll().get(0).getProvider()).isEqualTo("MAILJET");
    }

    @Test
    void rejectsInvalidRecipientWithoutSending() {
        NotificationEventEnvelope envelope = orderCreatedEnvelope(UUID.randomUUID(), "invalid-email");

        assertThatThrownBy(() -> processingService.process(envelope, METADATA))
                .isInstanceOf(NonRetryableNotificationException.class)
                .hasMessageContaining("Invalid customerEmail");

        assertThat(notificationRepository.count()).isZero();
        assertThat(attemptRepository.count()).isZero();
        assertThat(inboxRepository.findById(envelope.eventId()).orElseThrow().getStatus()).isEqualTo(InboxStatus.FAILED);
        verify(emailSender, times(0)).send(org.mockito.ArgumentMatchers.any(EmailMessage.class));
    }

    private NotificationEventEnvelope orderCreatedEnvelope(UUID eventId, String email) {
        var payload = new OrderCreatedPayload(
                42L,
                "AS-2026-00042",
                7L,
                "Ivan",
                "Petrov",
                email,
                12L,
                "Toyota",
                "Camry",
                "A123BC77",
                Instant.parse("2026-04-19T10:15:30Z")
        );
        return new NotificationEventEnvelope(
                eventId,
                "ORDER_CREATED",
                Instant.parse("2026-04-19T10:15:30Z"),
                "autoshop-core",
                1,
                "test-correlation-id",
                objectMapper.valueToTree(payload)
        );
    }
}
