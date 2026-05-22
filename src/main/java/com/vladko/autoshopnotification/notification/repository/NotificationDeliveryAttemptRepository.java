package com.vladko.autoshopnotification.notification.repository;

import com.vladko.autoshopnotification.notification.entity.NotificationDeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryAttemptRepository extends JpaRepository<NotificationDeliveryAttemptEntity, Long> {
}
