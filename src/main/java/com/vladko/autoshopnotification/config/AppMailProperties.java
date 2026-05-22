package com.vladko.autoshopnotification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        String provider,
        String from,
        String fromName
) {

    public AppMailProperties {
        provider = provider == null || provider.isBlank() ? "smtp" : provider;
    }
}
