package com.vladko.autoshopnotification.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retry")
public record AppRetryProperties(
        Retry email,
        Retry kafka
) {

    public record Retry(
            int maxAttempts,
            Duration backoff
    ) {
    }
}
