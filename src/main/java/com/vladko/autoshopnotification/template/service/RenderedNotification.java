package com.vladko.autoshopnotification.template.service;

import com.vladko.autoshopnotification.email.EmailMessage;

public record RenderedNotification(
        String templateKey,
        EmailMessage emailMessage
) {
}
