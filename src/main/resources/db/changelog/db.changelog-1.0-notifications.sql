-- liquibase formatted sql

-- changeset vladko:notification-1.0
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    source VARCHAR(80) NOT NULL,
    channel VARCHAR(30) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    template_key VARCHAR(120) NOT NULL,
    status VARCHAR(40) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    CONSTRAINT uk_notification_event_channel UNIQUE (event_id, channel)
);

CREATE TABLE notification_delivery_attempt (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notification(id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL,
    provider VARCHAR(60) NOT NULL,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP
);

CREATE TABLE notification_event_inbox (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    source VARCHAR(80) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    partition_number INTEGER,
    offset_number BIGINT,
    status VARCHAR(40) NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP,
    error_message TEXT
);

CREATE TABLE notification_template (
    id BIGSERIAL PRIMARY KEY,
    template_key VARCHAR(120) NOT NULL UNIQUE,
    channel VARCHAR(30) NOT NULL,
    subject_template VARCHAR(255) NOT NULL,
    body_template_path VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX ix_notification_status ON notification (status);
CREATE INDEX ix_notification_event_type ON notification (event_type);
CREATE INDEX ix_notification_inbox_status ON notification_event_inbox (status);

INSERT INTO notification_template (template_key, channel, subject_template, body_template_path, active, created_at, updated_at)
VALUES
('ORDER_CREATED_EMAIL', 'EMAIL', 'AutoShop: заказ {{orderNumber}} создан', 'email/order-created', TRUE, NOW(), NOW()),
('ORDER_STATUS_CHANGED_EMAIL', 'EMAIL', 'AutoShop: статус заказа {{orderNumber}} изменен', 'email/order-status-changed', TRUE, NOW(), NOW()),
('ORDER_COMPLETED_EMAIL', 'EMAIL', 'AutoShop: заказ {{orderNumber}} завершен', 'email/order-completed', TRUE, NOW(), NOW());
