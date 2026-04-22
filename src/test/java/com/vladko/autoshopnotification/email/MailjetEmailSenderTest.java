package com.vladko.autoshopnotification.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import com.vladko.autoshopnotification.config.AppMailProperties;
import com.vladko.autoshopnotification.config.AppMailjetProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MailjetEmailSenderTest {

    private static final URI SEND_URL = URI.create("https://api.mailjet.test/v3.1/send");

    @Test
    void sendsMailjetPayloadAndReturnsProviderMessageIds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailjetEmailSender sender = new MailjetEmailSender(mailProperties(), mailjetProperties(), builder.build());
        String authorization = "Basic " + Base64.getEncoder()
                .encodeToString("api-key:api-secret".getBytes(StandardCharsets.UTF_8));

        server.expect(requestTo(SEND_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$.SandboxMode").value(true))
                .andExpect(jsonPath("$.Messages[0].From.Email").value("sender@example.com"))
                .andExpect(jsonPath("$.Messages[0].From.Name").value("AutoShop"))
                .andExpect(jsonPath("$.Messages[0].To[0].Email").value("ivan@example.com"))
                .andExpect(jsonPath("$.Messages[0].Subject").value("Subject"))
                .andExpect(jsonPath("$.Messages[0].HTMLPart").value("<p>Hello</p>"))
                .andExpect(jsonPath("$.Messages[0].CustomID").value("event-123"))
                .andRespond(withSuccess("""
                        {
                          "Messages": [
                            {
                              "Status": "success",
                              "To": [
                                {
                                  "Email": "ivan@example.com",
                                  "MessageUUID": "uuid-123",
                                  "MessageID": 123456789,
                                  "MessageHref": "https://api.mailjet.com/v3/message/123456789"
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        EmailSendResult result = sender.send(new EmailMessage(
                "ivan@example.com",
                "Subject",
                "<p>Hello</p>",
                "ORDER_CREATED_EMAIL",
                "event-123",
                "eventType=ORDER_CREATED;template=ORDER_CREATED_EMAIL"
        ));

        assertThat(result.provider()).isEqualTo("MAILJET");
        assertThat(result.providerMessageId()).isEqualTo("123456789");
        assertThat(result.providerMessageUuid()).isEqualTo("uuid-123");
        assertThat(result.providerMessageHref()).isEqualTo("https://api.mailjet.com/v3/message/123456789");
        server.verify();
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailjetEmailSender sender = new MailjetEmailSender(mailProperties(), mailjetProperties(), builder.build());

        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(MailjetEmailException.class,
                        exception -> assertThat(exception.isRetryable()).isTrue());
        server.verify();
    }

    @Test
    void classifiesBadRequestAsNonRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MailjetEmailSender sender = new MailjetEmailSender(mailProperties(), mailjetProperties(), builder.build());

        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(MailjetEmailException.class,
                        exception -> assertThat(exception.isRetryable()).isFalse());
        server.verify();
    }

    private EmailMessage message() {
        return new EmailMessage(
                "ivan@example.com",
                "Subject",
                "<p>Hello</p>",
                "ORDER_CREATED_EMAIL",
                "event-123",
                "eventType=ORDER_CREATED;template=ORDER_CREATED_EMAIL"
        );
    }

    private AppMailProperties mailProperties() {
        return new AppMailProperties("mailjet", "sender@example.com", "AutoShop");
    }

    private AppMailjetProperties mailjetProperties() {
        return new AppMailjetProperties(
                "api-key",
                "api-secret",
                SEND_URL,
                true,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );
    }
}
