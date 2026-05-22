package com.vladko.autoshopnotification.retry;

public class NonRetryableNotificationException extends NotificationProcessingException {

    public NonRetryableNotificationException(String message) {
        super(message);
    }

    public NonRetryableNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
