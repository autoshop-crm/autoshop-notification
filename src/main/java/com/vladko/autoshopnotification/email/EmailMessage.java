package com.vladko.autoshopnotification.email;

public record EmailMessage(
        String recipient,
        String subject,
        String htmlBody,
        String templateKey
) {
}
