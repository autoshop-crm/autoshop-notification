package com.vladko.autoshopnotification.email;

public interface EmailSender {

    void send(EmailMessage message);
}
