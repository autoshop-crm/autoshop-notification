package com.vladko.autoshopnotification.template.service;

import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import org.springframework.stereotype.Component;

@Component
public class TemplateKeyResolver {

    public String resolve(String eventType) {
        return switch (eventType) {
            case "ORDER_CREATED" -> "ORDER_CREATED_EMAIL";
            case "ORDER_STATUS_CHANGED" -> "ORDER_STATUS_CHANGED_EMAIL";
            case "ORDER_COMPLETED" -> "ORDER_COMPLETED_EMAIL";
            default -> throw new NonRetryableNotificationException("Unsupported event type: " + eventType);
        };
    }
}
