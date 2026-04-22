package com.vladko.autoshopnotification.notification.repository;

import java.util.Optional;
import java.util.UUID;

import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    Optional<NotificationEntity> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);
}
