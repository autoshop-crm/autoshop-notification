package com.vladko.autoshopnotification.config;

import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            AppKafkaProperties kafkaProperties,
            AppRetryProperties retryProperties,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup
    ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate, kafkaProperties, retryProperties));
        return factory;
    }

    @Bean
    public NewTopic orderEventsTopic(AppKafkaProperties properties) {
        return TopicBuilder.name(properties.orderEventsTopic())
                .partitions(properties.topicPartitions())
                .replicas(properties.topicReplicas())
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic(AppKafkaProperties properties) {
        return TopicBuilder.name(properties.orderEventsDltTopic())
                .partitions(properties.topicPartitions())
                .replicas(properties.topicReplicas())
                .build();
    }

    private DefaultErrorHandler errorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            AppKafkaProperties kafkaProperties,
            AppRetryProperties retryProperties
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(kafkaProperties.orderEventsDltTopic(), record.partition()));
        var kafkaRetry = retryProperties.kafka();
        long retriesAfterFirstAttempt = Math.max(kafkaRetry.maxAttempts() - 1L, 0L);
        var handler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(kafkaRetry.backoff().toMillis(), retriesAfterFirstAttempt)
        );
        handler.addNotRetryableExceptions(NonRetryableNotificationException.class);
        return handler;
    }
}
