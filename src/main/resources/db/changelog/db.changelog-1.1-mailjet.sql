-- liquibase formatted sql

-- changeset vladko:notification-1.1-mailjet-provider
ALTER TABLE notification
    ADD COLUMN provider VARCHAR(60);

ALTER TABLE notification
    ADD COLUMN provider_message_id VARCHAR(80);

ALTER TABLE notification
    ADD COLUMN provider_message_uuid VARCHAR(120);

ALTER TABLE notification
    ADD COLUMN provider_message_href TEXT;

ALTER TABLE notification
    ADD COLUMN provider_accepted_at TIMESTAMP;

CREATE INDEX ix_notification_provider_message_id ON notification (provider_message_id);
CREATE INDEX ix_notification_provider_message_uuid ON notification (provider_message_uuid);
