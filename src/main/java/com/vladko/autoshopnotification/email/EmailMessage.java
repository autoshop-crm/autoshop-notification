package com.vladko.autoshopnotification.email;

public record EmailMessage(
        String recipient,
        String subject,
        String htmlBody,
        String templateKey,
        String customId,
        String eventPayload
) {

    public EmailMessage(String recipient, String subject, String htmlBody, String templateKey) {
        this(recipient, subject, htmlBody, templateKey, null, null);
    }
}
