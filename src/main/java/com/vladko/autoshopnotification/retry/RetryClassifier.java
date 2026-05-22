package com.vladko.autoshopnotification.retry;

import com.vladko.autoshopnotification.email.MailjetEmailException;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.stereotype.Component;

@Component
public class RetryClassifier {

    public boolean isRetryable(MailException exception) {
        if (exception instanceof MailjetEmailException mailjetEmailException) {
            return mailjetEmailException.isRetryable();
        }
        return !(exception instanceof MailAuthenticationException
                || exception instanceof MailParseException
                || exception instanceof MailPreparationException);
    }
}
