package com.vladko.autoshopnotification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(
        String orderEventsTopic,
        String orderEventsDltTopic,
        int topicPartitions,
        short topicReplicas
) {
}
