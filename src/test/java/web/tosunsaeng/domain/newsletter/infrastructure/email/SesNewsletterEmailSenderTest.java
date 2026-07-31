package web.tosunsaeng.domain.newsletter.infrastructure.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.MessageHeader;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendException;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesNewsletterEmailSenderTest {

    @Mock
    private SesV2Client sesV2Client;

    private SesNewsletterEmailSender sender;

    @BeforeEach
    void setUp() {
        NewsletterDeliveryProperties properties = new NewsletterDeliveryProperties();
        properties.setFromEmail("newsletter@example.test");
        properties.setFromName("토선생");
        sender = new SesNewsletterEmailSender(sesV2Client, properties);
    }

    @Test
    void simpleMessageMapsHtmlPlainTextAndBothCustomHeaders() {
        SendEmailRequest request = sender.toRequest(message());

        assertThat(request.fromEmailAddress())
                .isEqualTo("토선생 <newsletter@example.test>");
        assertThat(request.destination().toAddresses())
                .containsExactly("user@example.test");
        assertThat(request.content().simple()).isNotNull();
        assertThat(request.content().simple().subject().data())
                .isEqualTo("[토선생] 제목");
        assertThat(request.content().simple().subject().charset()).isEqualTo("UTF-8");
        assertThat(request.content().simple().body().html().data())
                .isEqualTo("<p>html</p>");
        assertThat(request.content().simple().body().text().data()).isEqualTo("plain");
        assertThat(request.content().simple().headers())
                .extracting(MessageHeader::name, MessageHeader::value)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
                                "<https://api.example.test/one-click/token>"),
                        org.assertj.core.groups.Tuple.tuple(
                                NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST,
                                NewsletterLinkBuilder.ONE_CLICK_FORM_VALUE));
    }

    @Test
    void successfulSesResponseReturnsOnlyProviderMessageId() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder()
                        .messageId("ses-message-id")
                        .build());

        NewsletterEmailSendResult result = sender.send(message());

        ArgumentCaptor<SendEmailRequest> captor =
                ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesV2Client).sendEmail(captor.capture());
        assertThat(result.providerMessageId()).isEqualTo("ses-message-id");
        assertThat(captor.getValue().content().simple().headers()).hasSize(2);
    }

    @Test
    void throttlingIsRetryableButAuthAndBadRequestsArePermanent() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesV2Exception.builder().statusCode(429).build());
        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(NewsletterEmailSendException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(NewsletterFailureType.TRANSIENT_PROVIDER);
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.isProviderResultKnown()).isTrue();
                });

        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesV2Exception.builder().statusCode(403).build());
        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(NewsletterEmailSendException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(NewsletterFailureType.AUTH_CONFIGURATION);
                    assertThat(exception.isRetryable()).isFalse();
                });

        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesV2Exception.builder().statusCode(400).build());
        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(NewsletterEmailSendException.class, exception ->
                        assertThat(exception.getFailureType())
                                .isEqualTo(NewsletterFailureType.PERMANENT_REQUEST));
    }

    @Test
    void clientFailureIsUnknownAndDoesNotExposeProviderDetails() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SdkClientException.create(
                        "timeout for sensitive@example.test signed-token"));

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(NewsletterEmailSendException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(NewsletterFailureType.PROVIDER_RESULT_UNKNOWN);
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.isProviderResultKnown()).isFalse();
                    assertThat(exception.getMessage()).doesNotContain(
                            "sensitive@example.test", "signed-token", "timeout");
                });
    }

    @Test
    void safeSesErrorCodeDistinguishesConfigurationFailure() {
        when(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(SesV2Exception.builder()
                        .statusCode(400)
                        .awsErrorDetails(AwsErrorDetails.builder()
                                .errorCode("MailFromDomainNotVerifiedException")
                                .errorMessage("sensitive provider detail")
                                .build())
                        .build());

        assertThatThrownBy(() -> sender.send(message()))
                .isInstanceOfSatisfying(NewsletterEmailSendException.class, exception -> {
                    assertThat(exception.getFailureType())
                            .isEqualTo(NewsletterFailureType.AUTH_CONFIGURATION);
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getMessage())
                            .doesNotContain("sensitive provider detail");
                });
    }

    @Test
    void rejectsUnapprovedOrHeaderInjectedCustomHeaders() {
        NewsletterEmailMessage unapproved = new NewsletterEmailMessage(
                "user@example.test",
                "subject",
                "html",
                "plain",
                Map.of("X-Custom", "value"));
        assertThatThrownBy(() -> sender.toRequest(unapproved))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("value");

        NewsletterEmailMessage injected = new NewsletterEmailMessage(
                "user@example.test",
                "subject",
                "html",
                "plain",
                Map.of(NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
                        "<https://api/token>\r\nBcc: victim@example.test"));
        assertThatThrownBy(() -> sender.toRequest(injected))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("victim@example.test");
    }

    private NewsletterEmailMessage message() {
        return new NewsletterEmailMessage(
                "user@example.test",
                "[토선생] 제목",
                "<p>html</p>",
                "plain",
                Map.of(
                        NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
                        "<https://api.example.test/one-click/token>",
                        NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST,
                        NewsletterLinkBuilder.ONE_CLICK_FORM_VALUE));
    }
}
