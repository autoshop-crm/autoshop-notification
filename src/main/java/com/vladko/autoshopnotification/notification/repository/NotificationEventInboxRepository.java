package com.vladko.autoshopnotification.notification.repository;

import java.util.UUID;

import com.vladko.autoshopnotification.notification.entity.NotificationEventInboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventInboxRepository extends JpaRepository<NotificationEventInboxEntity, UUID> {
}
