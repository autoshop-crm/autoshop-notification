package com.vladko.autoshopnotification.template.service;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladko.autoshopnotification.email.EmailMessage;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.event.dto.OrderCompletedPayload;
import com.vladko.autoshopnotification.event.dto.OrderCreatedPayload;
import com.vladko.autoshopnotification.event.dto.OrderStatusChangedPayload;
import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import com.vladko.autoshopnotification.template.entity.NotificationTemplateEntity;
import com.vladko.autoshopnotification.template.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class NotificationTemplateService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Locale RU_LOCALE = Locale.forLanguageTag("ru-RU");
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", RU_LOCALE).withZone(MOSCOW_ZONE);

    private final ObjectMapper objectMapper;
    private final TemplateKeyResolver templateKeyResolver;
    private final StatusLabelMapper statusLabelMapper;
    private final NotificationTemplateRepository templateRepository;
    private final TemplateEngine templateEngine;

    public NotificationTemplateService(ObjectMapper objectMapper,
                                       TemplateKeyResolver templateKeyResolver,
                                       StatusLabelMapper statusLabelMapper,
                                       NotificationTemplateRepository templateRepository,
                                       TemplateEngine templateEngine) {
        this.objectMapper = objectMapper;
        this.templateKeyResolver = templateKeyResolver;
        this.statusLabelMapper = statusLabelMapper;
        this.templateRepository = templateRepository;
        this.templateEngine = templateEngine;
    }

    public RenderedNotification render(NotificationEventEnvelope envelope) {
        String templateKey = templateKeyResolver.resolve(envelope.eventType());
        NotificationTemplateEntity template = templateRepository
                .findByTemplateKeyAndChannelAndActiveTrue(templateKey, NotificationChannel.EMAIL)
                .orElseThrow(() -> new NonRetryableNotificationException("Active email template not found: " + templateKey));

        Map<String, Object> variables = variablesFor(envelope);
        String recipient = requiredEmail(variables.get("customerEmail"));
        String subject = renderSubject(template.getSubjectTemplate(), variables);

        Context context = new Context(RU_LOCALE);
        context.setVariables(variables);
        String html = templateEngine.process(template.getBodyTemplatePath(), context);

        String customId = envelope.eventId().toString();
        String eventPayload = "eventType=" + envelope.eventType() + ";template=" + templateKey;
        return new RenderedNotification(templateKey, new EmailMessage(recipient, subject, html, templateKey, customId, eventPayload));
    }

    private Map<String, Object> variablesFor(NotificationEventEnvelope envelope) {
        return switch (envelope.eventType()) {
            case "ORDER_CREATED" -> orderCreatedVariables(toPayload(envelope, OrderCreatedPayload.class));
            case "ORDER_STATUS_CHANGED" -> orderStatusChangedVariables(toPayload(envelope, OrderStatusChangedPayload.class));
            case "ORDER_COMPLETED" -> orderCompletedVariables(toPayload(envelope, OrderCompletedPayload.class));
            default -> throw new NonRetryableNotificationException("Unsupported event type: " + envelope.eventType());
        };
    }

    private Map<String, Object> orderCreatedVariables(OrderCreatedPayload payload) {
        require(payload.orderNumber(), "orderNumber");
        require(payload.customerEmail(), "customerEmail");
        return Map.of(
                "customerFirstName", valueOrDefault(payload.customerFirstName(), "клиент"),
                "customerEmail", payload.customerEmail(),
                "orderNumber", payload.orderNumber(),
                "vehicleBrand", valueOrDefault(payload.vehicleBrand(), ""),
                "vehicleModel", valueOrDefault(payload.vehicleModel(), ""),
                "vehiclePlateNumber", valueOrDefault(payload.vehiclePlateNumber(), ""),
                "createdAt", formatDateTime(payload.createdAt())
        );
    }

    private Map<String, Object> orderStatusChangedVariables(OrderStatusChangedPayload payload) {
        require(payload.orderNumber(), "orderNumber");
        require(payload.customerEmail(), "customerEmail");
        require(payload.newStatus(), "newStatus");
        if (payload.previousStatus() != null && payload.previousStatus().equals(payload.newStatus())) {
            throw new NonRetryableNotificationException("Status change event has equal previousStatus and newStatus");
        }
        return Map.of(
                "customerFirstName", valueOrDefault(payload.customerFirstName(), "клиент"),
                "customerEmail", payload.customerEmail(),
                "orderNumber", payload.orderNumber(),
                "previousStatusLabel", statusLabelMapper.toLabel(payload.previousStatus()),
                "newStatusLabel", statusLabelMapper.toLabel(payload.newStatus()),
                "changedAt", formatDateTime(payload.changedAt()),
                "managerComment", valueOrDefault(payload.managerComment(), "")
        );
    }

    private Map<String, Object> orderCompletedVariables(OrderCompletedPayload payload) {
        require(payload.orderNumber(), "orderNumber");
        require(payload.customerEmail(), "customerEmail");
        int loyaltyPointsEarned = payload.loyaltyPointsEarned() == null ? 0 : payload.loyaltyPointsEarned();
        String currency = valueOrDefault(payload.currency(), "RUB");
        return Map.of(
                "customerFirstName", valueOrDefault(payload.customerFirstName(), "клиент"),
                "customerEmail", payload.customerEmail(),
                "orderNumber", payload.orderNumber(),
                "completedAt", formatDateTime(payload.completedAt()),
                "finalAmount", payload.finalAmount() == null ? "" : formatMoney(payload.finalAmount(), currency),
                "hasFinalAmount", payload.finalAmount() != null,
                "currency", currency,
                "loyaltyPointsEarned", loyaltyPointsEarned,
                "hasLoyaltyPoints", loyaltyPointsEarned > 0
        );
    }

    private <T> T toPayload(NotificationEventEnvelope envelope, Class<T> type) {
        if (envelope.payload() == null || envelope.payload().isNull()) {
            throw new NonRetryableNotificationException("Event payload is required");
        }
        return objectMapper.convertValue(envelope.payload(), type);
    }

    private String requiredEmail(Object value) {
        String email = value == null ? null : value.toString();
        require(email, "customerEmail");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new NonRetryableNotificationException("Invalid customerEmail");
        }
        return email;
    }

    private String renderSubject(String subjectTemplate, Map<String, Object> variables) {
        String subject = subjectTemplate;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            subject = subject.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return subject;
    }

    private void require(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new NonRetryableNotificationException("Missing required payload field: " + fieldName);
        }
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String formatDateTime(java.time.Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatMoney(java.math.BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getNumberInstance(RU_LOCALE);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " " + currency;
    }
}
