package com.vladko.autoshopnotification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladko.autoshopnotification.event.dto.EventMetadata;
import com.vladko.autoshopnotification.event.dto.NotificationEventEnvelope;
import com.vladko.autoshopnotification.notification.service.NotificationProcessingService;
import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationProcessingService processingService;

    public NotificationEventConsumer(ObjectMapper objectMapper,
                                     NotificationProcessingService processingService) {
        this.objectMapper = objectMapper;
        this.processingService = processingService;
    }

    @KafkaListener(topics = "${app.kafka.order-events-topic}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        NotificationEventEnvelope envelope = readEnvelope(record.value());
        log.info("Received notification event eventId={} eventType={} topic={} partition={} offset={}",
                envelope.eventId(), envelope.eventType(), record.topic(), record.partition(), record.offset());
        processingService.process(envelope, new EventMetadata(record.topic(), record.partition(), record.offset()));
    }

    private NotificationEventEnvelope readEnvelope(String rawMessage) {
        try {
            return objectMapper.readValue(rawMessage, NotificationEventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableNotificationException("Invalid notification event JSON", exception);
        }
    }
}
