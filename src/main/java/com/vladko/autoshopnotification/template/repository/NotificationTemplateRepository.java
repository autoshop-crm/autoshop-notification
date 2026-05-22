package com.vladko.autoshopnotification.template.repository;

import java.util.Optional;

import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import com.vladko.autoshopnotification.template.entity.NotificationTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {

    Optional<NotificationTemplateEntity> findByTemplateKeyAndChannelAndActiveTrue(
            String templateKey,
            NotificationChannel channel
    );
}
