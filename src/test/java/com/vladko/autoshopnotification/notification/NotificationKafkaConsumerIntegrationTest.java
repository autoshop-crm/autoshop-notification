package com.vladko.autoshopnotification.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladko.autoshopnotification.email.EmailMessage;
import com.vladko.autoshopnotification.email.EmailSender;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.event.dto.OrderCompletedPayload;
import com.vladko.autoshopnotification.event.dto.OrderCreatedPayload;
import com.vladko.autoshopnotification.event.dto.OrderStatusChangedPayload;
import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.notification.entity.NotificationStatus;
import com.vladko.autoshopnotification.notification.repository.NotificationDeliveryAttemptRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationEventInboxRepository;
import com.vladko.autoshopnotification.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"autoshop.order-events", "autoshop.order-events.dlt"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.auto-create=false",
        "app.retry.kafka.max-attempts=1",
        "app.retry.kafka.backoff=1ms"
})
@DirtiesContext
class NotificationKafkaConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationEventInboxRepository inboxRepository;

    @Autowired
    private NotificationDeliveryAttemptRepository attemptRepository;

    @MockitoBean
    private EmailSender emailSender;

    @BeforeEach
    void cleanDatabase() {
        reset(emailSender);
        attemptRepository.deleteAll();
        notificationRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void consumesOrderCreatedEventAndSendsEmail() throws Exception {
        NotificationEventEnvelope envelope = orderCreatedEnvelope();

        kafkaTemplate.send("autoshop.order-events", envelope.eventId().toString(), objectMapper.writeValueAsString(envelope))
                .get(5, TimeUnit.SECONDS);

        verify(emailSender, timeout(5000).times(1)).send(any(EmailMessage.class));

        waitUntilNotificationStatus(envelope.eventId(), NotificationStatus.SENT);
        var notification = notificationRepository
                .findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(attemptRepository.count()).isEqualTo(1);
    }

    @Test
    void consumesOrderStatusChangedEventAndSendsEmail() throws Exception {
        NotificationEventEnvelope envelope = orderStatusChangedEnvelope();

        kafkaTemplate.send("autoshop.order-events", envelope.eventId().toString(), objectMapper.writeValueAsString(envelope))
                .get(5, TimeUnit.SECONDS);

        verify(emailSender, timeout(5000).times(1)).send(any(EmailMessage.class));

        waitUntilNotificationStatus(envelope.eventId(), NotificationStatus.SENT);
        var notification = notificationRepository
                .findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getTemplateKey()).isEqualTo("ORDER_STATUS_CHANGED_EMAIL");
        assertThat(attemptRepository.count()).isEqualTo(1);
    }

    @Test
    void consumesOrderCompletedEventAndSendsEmail() throws Exception {
        NotificationEventEnvelope envelope = orderCompletedEnvelope();

        kafkaTemplate.send("autoshop.order-events", envelope.eventId().toString(), objectMapper.writeValueAsString(envelope))
                .get(5, TimeUnit.SECONDS);

        verify(emailSender, timeout(5000).times(1)).send(any(EmailMessage.class));

        waitUntilNotificationStatus(envelope.eventId(), NotificationStatus.SENT);
        var notification = notificationRepository
                .findByEventIdAndChannel(envelope.eventId(), NotificationChannel.EMAIL)
                .orElseThrow();
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.getTemplateKey()).isEqualTo("ORDER_COMPLETED_EMAIL");
        assertThat(attemptRepository.count()).isEqualTo(1);
    }

    @Test
    void duplicateEventIdDoesNotSendSecondEmail() throws Exception {
        NotificationEventEnvelope envelope = orderCreatedEnvelope();
        String json = objectMapper.writeValueAsString(envelope);

        kafkaTemplate.send("autoshop.order-events", envelope.eventId().toString(), json)
                .get(5, TimeUnit.SECONDS);
        verify(emailSender, timeout(5000).times(1)).send(any(EmailMessage.class));
        waitUntilNotificationStatus(envelope.eventId(), NotificationStatus.SENT);

        kafkaTemplate.send("autoshop.order-events", envelope.eventId().toString(), json)
                .get(5, TimeUnit.SECONDS);

        verify(emailSender, after(1000).times(1)).send(any(EmailMessage.class));
        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(attemptRepository.count()).isEqualTo(1);
    }

    private void waitUntilNotificationStatus(UUID eventId, NotificationStatus expectedStatus) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            var notification = notificationRepository.findByEventIdAndChannel(eventId, NotificationChannel.EMAIL);
            if (notification.isPresent() && notification.get().getStatus() == expectedStatus) {
                return;
            }
            Thread.sleep(50);
        }
    }

    private NotificationEventEnvelope orderCreatedEnvelope() {
        var payload = new OrderCreatedPayload(
                42L,
                "AS-2026-00042",
                7L,
                "Ivan",
                "Petrov",
                "ivan@example.com",
                12L,
                "Toyota",
                "Camry",
                "A123BC77",
                Instant.parse("2026-04-19T10:15:30Z")
        );
        return new NotificationEventEnvelope(
                UUID.randomUUID(),
                "ORDER_CREATED",
                Instant.parse("2026-04-19T10:15:30Z"),
                "autoshop-core",
                1,
                "test-correlation-id",
                objectMapper.valueToTree(payload)
        );
    }

    private NotificationEventEnvelope orderStatusChangedEnvelope() {
        var payload = new OrderStatusChangedPayload(
                42L,
                "AS-2026-00042",
                7L,
                "Ivan",
                "Petrov",
                "ivan@example.com",
                "NEW",
                "IN_PROGRESS",
                Instant.parse("2026-04-19T12:00:00Z"),
                ""
        );
        return new NotificationEventEnvelope(
                UUID.randomUUID(),
                "ORDER_STATUS_CHANGED",
                Instant.parse("2026-04-19T12:00:00Z"),
                "autoshop-core",
                1,
                "test-correlation-id",
                objectMapper.valueToTree(payload)
        );
    }

    private NotificationEventEnvelope orderCompletedEnvelope() {
        var payload = new OrderCompletedPayload(
                42L,
                "AS-2026-00042",
                7L,
                "Ivan",
                "Petrov",
                "ivan@example.com",
                Instant.parse("2026-04-19T18:30:00Z"),
                new BigDecimal("24500.00"),
                "RUB",
                245
        );
        return new NotificationEventEnvelope(
                UUID.randomUUID(),
                "ORDER_COMPLETED",
                Instant.parse("2026-04-19T18:30:00Z"),
                "autoshop-core",
                1,
                "test-correlation-id",
                objectMapper.valueToTree(payload)
        );
    }
}
