package com.vladko.autoshopnotification.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.vladko.autoshopnotification.template.service.StatusLabelMapper;
import org.junit.jupiter.api.Test;

class StatusLabelMapperTest {

    private final StatusLabelMapper mapper = new StatusLabelMapper();

    @Test
    void mapsKnownStatusesToRussianLabels() {
        assertThat(mapper.toLabel("NEW")).isEqualTo("Новый");
        assertThat(mapper.toLabel("IN_PROGRESS")).isEqualTo("В работе");
        assertThat(mapper.toLabel("COMPLETED")).isEqualTo("Завершен");
        assertThat(mapper.toLabel("CANCELLED")).isEqualTo("Отменен");
    }

    @Test
    void keepsUnknownStatusReadable() {
        assertThat(mapper.toLabel("WAITING_PARTS")).isEqualTo("WAITING_PARTS");
    }
}
