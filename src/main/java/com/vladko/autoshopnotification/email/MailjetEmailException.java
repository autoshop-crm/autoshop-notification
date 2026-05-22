package com.vladko.autoshopnotification.email;

import org.springframework.mail.MailException;

public class MailjetEmailException extends MailException {

    private final boolean retryable;

    public MailjetEmailException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public MailjetEmailException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
