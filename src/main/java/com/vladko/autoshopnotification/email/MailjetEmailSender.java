package com.vladko.autoshopnotification.email;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vladko.autoshopnotification.config.AppMailProperties;
import com.vladko.autoshopnotification.config.AppMailjetProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@ConditionalOnProperty(prefix = "app.mail", name = "provider", havingValue = "mailjet")
public class MailjetEmailSender implements EmailSender {

    private static final String SUCCESS_STATUS = "success";

    private final AppMailProperties mailProperties;
    private final AppMailjetProperties mailjetProperties;
    private final RestClient restClient;

    public MailjetEmailSender(AppMailProperties mailProperties,
                              AppMailjetProperties mailjetProperties,
                              RestClient.Builder restClientBuilder) {
        this(mailProperties, mailjetProperties, restClientBuilder
                .requestFactory(requestFactory(mailjetProperties))
                .build());
    }

    MailjetEmailSender(AppMailProperties mailProperties,
                       AppMailjetProperties mailjetProperties,
                       RestClient restClient) {
        this.mailProperties = mailProperties;
        this.mailjetProperties = mailjetProperties;
        this.restClient = restClient;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        validateConfiguration();
        MailjetSendRequest request = toMailjetRequest(message);
        MailjetSendResponse response;
        try {
            response = restClient.post()
                    .uri(mailjetProperties.sendUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBasicAuth(
                            mailjetProperties.apiKey(),
                            mailjetProperties.apiSecret(),
                            StandardCharsets.UTF_8
                    ))
                    .body(request)
                    .retrieve()
                    .body(MailjetSendResponse.class);
        } catch (RestClientResponseException exception) {
            throw toMailjetException(exception);
        } catch (ResourceAccessException exception) {
            throw new MailjetEmailException("Mailjet API is temporarily unavailable", true, exception);
        } catch (RestClientException exception) {
            throw new MailjetEmailException("Mailjet API request failed", true, exception);
        }

        return toEmailSendResult(response);
    }

    @Override
    public String providerName() {
        return "MAILJET";
    }

    private MailjetSendRequest toMailjetRequest(EmailMessage message) {
        MailjetAddress from = new MailjetAddress(mailProperties.from(), mailProperties.fromName());
        MailjetAddress to = new MailjetAddress(message.recipient(), null);
        MailjetMessage mailjetMessage = new MailjetMessage(
                from,
                List.of(to),
                message.subject(),
                message.htmlBody(),
                message.customId(),
                message.eventPayload()
        );
        return new MailjetSendRequest(mailjetProperties.sandboxMode(), List.of(mailjetMessage));
    }

    private EmailSendResult toEmailSendResult(MailjetSendResponse response) {
        if (response == null || response.messages() == null || response.messages().isEmpty()) {
            throw new MailjetEmailException("Mailjet API returned an empty response", true);
        }

        MailjetSendMessageResult messageResult = response.messages().get(0);
        if (!SUCCESS_STATUS.equalsIgnoreCase(messageResult.status())) {
            throw new MailjetEmailException("Mailjet rejected email: " + errorMessage(messageResult), false);
        }

        MailjetRecipientResult recipientResult = firstRecipient(messageResult);
        return new EmailSendResult(
                providerName(),
                recipientResult == null || recipientResult.messageId() == null ? null : recipientResult.messageId().toString(),
                recipientResult == null ? null : recipientResult.messageUuid(),
                recipientResult == null ? null : recipientResult.messageHref()
        );
    }

    private MailjetRecipientResult firstRecipient(MailjetSendMessageResult messageResult) {
        if (messageResult.to() == null || messageResult.to().isEmpty()) {
            return null;
        }
        return messageResult.to().get(0);
    }

    private String errorMessage(MailjetSendMessageResult messageResult) {
        if (messageResult.errors() == null || messageResult.errors().isEmpty()) {
            return "status=" + messageResult.status();
        }
        return messageResult.errors().stream()
                .map(MailjetError::errorMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("status=" + messageResult.status());
    }

    private MailjetEmailException toMailjetException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        boolean retryable = status == 408 || status == 429 || status >= 500;
        return new MailjetEmailException(
                "Mailjet API request failed with HTTP " + status + ": " + exception.getStatusText(),
                retryable,
                exception
        );
    }

    private void validateConfiguration() {
        if (isBlank(mailjetProperties.apiKey()) || isBlank(mailjetProperties.apiSecret())) {
            throw new MailjetEmailException("Mailjet API credentials are required", false);
        }
        if (isBlank(mailProperties.from())) {
            throw new MailjetEmailException("Mailjet sender email is required", false);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static SimpleClientHttpRequestFactory requestFactory(AppMailjetProperties mailjetProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(mailjetProperties.connectTimeout());
        requestFactory.setReadTimeout(mailjetProperties.readTimeout());
        return requestFactory;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MailjetSendRequest(
            @JsonProperty("SandboxMode") boolean sandboxMode,
            @JsonProperty("Messages") List<MailjetMessage> messages
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MailjetMessage(
            @JsonProperty("From") MailjetAddress from,
            @JsonProperty("To") List<MailjetAddress> to,
            @JsonProperty("Subject") String subject,
            @JsonProperty("HTMLPart") String htmlPart,
            @JsonProperty("CustomID") String customId,
            @JsonProperty("EventPayload") String eventPayload
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record MailjetAddress(
            @JsonProperty("Email") String email,
            @JsonProperty("Name") String name
    ) {
    }

    private record MailjetSendResponse(
            @JsonProperty("Messages") List<MailjetSendMessageResult> messages
    ) {
    }

    private record MailjetSendMessageResult(
            @JsonProperty("Status") String status,
            @JsonProperty("To") List<MailjetRecipientResult> to,
            @JsonProperty("Errors") List<MailjetError> errors
    ) {
    }

    private record MailjetRecipientResult(
            @JsonProperty("Email") String email,
            @JsonProperty("MessageUUID") String messageUuid,
            @JsonProperty("MessageID") Long messageId,
            @JsonProperty("MessageHref") String messageHref
    ) {
    }

    private record MailjetError(
            @JsonProperty("ErrorMessage") String errorMessage
    ) {
    }
}
