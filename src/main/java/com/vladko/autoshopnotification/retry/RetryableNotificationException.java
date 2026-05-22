package com.vladko.autoshopnotification.retry;

public class RetryableNotificationException extends NotificationProcessingException {

    public RetryableNotificationException(String message) {
        super(message);
    }

    public RetryableNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
