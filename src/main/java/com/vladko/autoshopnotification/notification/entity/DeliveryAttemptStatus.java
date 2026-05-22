package com.vladko.autoshopnotification.notification.entity;

public enum DeliveryAttemptStatus {
    STARTED,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_NON_RETRYABLE
}
