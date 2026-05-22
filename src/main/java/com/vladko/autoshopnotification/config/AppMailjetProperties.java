package com.vladko.autoshopnotification.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mailjet")
public record AppMailjetProperties(
        String apiKey,
        String apiSecret,
        URI sendUrl,
        Boolean sandboxMode,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AppMailjetProperties {
        sendUrl = sendUrl == null ? URI.create("https://api.mailjet.com/v3.1/send") : sendUrl;
        sandboxMode = sandboxMode == null ? true : sandboxMode;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
    }
}
