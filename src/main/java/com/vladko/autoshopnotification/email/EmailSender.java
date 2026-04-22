package com.vladko.autoshopnotification.email;

public interface EmailSender {

    EmailSendResult send(EmailMessage message);

    default String providerName() {
        return "EMAIL";
    }
}
