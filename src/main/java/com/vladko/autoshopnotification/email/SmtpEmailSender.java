package com.vladko.autoshopnotification.email;

import java.io.UnsupportedEncodingException;

import com.vladko.autoshopnotification.config.AppMailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppMailProperties mailProperties;

    public SmtpEmailSender(JavaMailSender mailSender, AppMailProperties mailProperties) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(mailProperties.from(), mailProperties.fromName());
            helper.setTo(message.recipient());
            helper.setSubject(message.subject());
            helper.setText(message.htmlBody(), true);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new MailPreparationException("Failed to prepare email message", exception);
        }
        mailSender.send(mimeMessage);
        return EmailSendResult.accepted(providerName());
    }

    @Override
    public String providerName() {
        return "SMTP";
    }
}
