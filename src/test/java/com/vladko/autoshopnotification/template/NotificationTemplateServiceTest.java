package com.vladko.autoshopnotification.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.event.dto.OrderCompletedPayload;
import com.vladko.autoshopnotification.event.dto.OrderStatusChangedPayload;
import com.vladko.autoshopnotification.template.service.NotificationTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationTemplateServiceTest {

    @Autowired
    private NotificationTemplateService templateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void rendersOrderStatusChangedWithoutBlankCommentBlock() {
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
        var rendered = templateService.render(envelope("ORDER_STATUS_CHANGED", payload));

        assertThat(rendered.templateKey()).isEqualTo("ORDER_STATUS_CHANGED_EMAIL");
        assertThat(rendered.emailMessage().recipient()).isEqualTo("ivan@example.com");
        assertThat(rendered.emailMessage().subject()).isEqualTo("AutoShop: статус заказа AS-2026-00042 изменен");
        assertThat(rendered.emailMessage().htmlBody()).contains("В работе");
        assertThat(rendered.emailMessage().htmlBody()).doesNotContain("Комментарий:");
    }

    @Test
    void rendersOrderCompletedWithMoneyAndLoyaltyPoints() {
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
        var rendered = templateService.render(envelope("ORDER_COMPLETED", payload));

        assertThat(rendered.templateKey()).isEqualTo("ORDER_COMPLETED_EMAIL");
        assertThat(rendered.emailMessage().htmlBody()).contains("AS-2026-00042");
        assertThat(rendered.emailMessage().htmlBody()).contains("245");
        assertThat(rendered.emailMessage().htmlBody()).contains("RUB");
    }

    private NotificationEventEnvelope envelope(String eventType, Object payload) {
        return new NotificationEventEnvelope(
                UUID.randomUUID(),
                eventType,
                Instant.parse("2026-04-19T10:15:30Z"),
                "autoshop-core",
                1,
                "test-correlation-id",
                objectMapper.valueToTree(payload)
        );
    }
}
