package com.vladko.autoshopnotification.template.service;

import org.springframework.stereotype.Component;

@Component
public class StatusLabelMapper {

    public String toLabel(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        return switch (status) {
            case "NEW" -> "Новый";
            case "IN_PROGRESS" -> "В работе";
            case "COMPLETED" -> "Завершен";
            case "CANCELLED" -> "Отменен";
            default -> status;
        };
    }
}
