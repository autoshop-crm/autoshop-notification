package com.vladko.autoshopnotification.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vladko.autoshopnotification.retry.NonRetryableNotificationException;
import com.vladko.autoshopnotification.template.service.TemplateKeyResolver;
import org.junit.jupiter.api.Test;

class TemplateKeyResolverTest {

    private final TemplateKeyResolver resolver = new TemplateKeyResolver();

    @Test
    void resolvesSupportedOrderEvents() {
        assertThat(resolver.resolve("ORDER_CREATED")).isEqualTo("ORDER_CREATED_EMAIL");
        assertThat(resolver.resolve("ORDER_STATUS_CHANGED")).isEqualTo("ORDER_STATUS_CHANGED_EMAIL");
        assertThat(resolver.resolve("ORDER_COMPLETED")).isEqualTo("ORDER_COMPLETED_EMAIL");
    }

    @Test
    void rejectsUnknownEventType() {
        assertThatThrownBy(() -> resolver.resolve("LOYALTY_POINTS_EARNED"))
                .isInstanceOf(NonRetryableNotificationException.class)
                .hasMessageContaining("Unsupported event type");
    }
}
