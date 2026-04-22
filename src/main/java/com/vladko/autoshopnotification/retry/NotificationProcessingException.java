package com.vladko.autoshopnotification.retry;

public abstract class NotificationProcessingException extends RuntimeException {

    protected NotificationProcessingException(String message) {
        super(message);
    }

    protected NotificationProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
