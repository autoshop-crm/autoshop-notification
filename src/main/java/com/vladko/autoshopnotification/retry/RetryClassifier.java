package com.vladko.autoshopnotification.retry;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;

@Component
public class RetryClassifier {

    public boolean isRetryable(MailException exception) {
        return !(exception instanceof MailAuthenticationException
                || exception instanceof MailParseException
                || exception instanceof MailPreparationException);
    }
}
