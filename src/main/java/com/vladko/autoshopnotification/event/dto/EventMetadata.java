package com.vladko.autoshopnotification.event.dto;

public record EventMetadata(
        String topic,
        Integer partition,
        Long offset
) {
}
