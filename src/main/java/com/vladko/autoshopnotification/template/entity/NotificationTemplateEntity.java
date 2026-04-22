package com.vladko.autoshopnotification.template.entity;

import java.time.Instant;

import com.vladko.autoshopnotification.notification.entity.NotificationChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_template")
public class NotificationTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, unique = true, length = 120)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 30)
    private NotificationChannel channel;

    @Column(name = "subject_template", nullable = false)
    private String subjectTemplate;

    @Column(name = "body_template_path", nullable = false)
    private String bodyTemplatePath;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationTemplateEntity() {
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getBodyTemplatePath() {
        return bodyTemplatePath;
    }
}
