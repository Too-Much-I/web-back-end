package web.tosunsaeng.domain.newsletter.infrastructure.email;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
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
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile("!local & !test")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "newsletter.delivery",
        name = "email-provider",
        havingValue = "ses")
public class SesNewsletterEmailSender implements NewsletterEmailSender {

    private static final Set<String> ALLOWED_HEADERS = Set.of(
            NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
            NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST);
    private static final Set<String> TRANSIENT_ERROR_CODES = Set.of(
            "TooManyRequestsException",
            "LimitExceededException");
    private static final Set<String> CONFIGURATION_ERROR_CODES = Set.of(
            "AccountSuspendedException",
            "MailFromDomainNotVerifiedException",
            "SendingPausedException");

    private final SesV2Client sesV2Client;
    private final NewsletterDeliveryProperties properties;

    @Override
    public NewsletterEmailSendResult send(NewsletterEmailMessage message) {
        try {
            SendEmailResponse response = sesV2Client.sendEmail(toRequest(message));
            return new NewsletterEmailSendResult(response.messageId());
        } catch (SesV2Exception exception) {
            throw classify(exception);
        } catch (SdkClientException exception) {
            throw new NewsletterEmailSendException(
                    NewsletterFailureType.PROVIDER_RESULT_UNKNOWN,
                    false,
                    false);
        }
    }

    SendEmailRequest toRequest(NewsletterEmailMessage message) {
        List<MessageHeader> headers = message.headers().entrySet().stream()
                .map(this::toHeader)
                .toList();
        Message simpleMessage = Message.builder()
                .subject(content(message.subject()))
                .body(Body.builder()
                        .html(content(message.htmlBody()))
                        .text(content(message.plainTextBody()))
                        .build())
                .headers(headers)
                .build();
        return SendEmailRequest.builder()
                .fromEmailAddress(fromAddress())
                .destination(Destination.builder()
                        .toAddresses(message.recipient())
                        .build())
                .content(EmailContent.builder().simple(simpleMessage).build())
                .build();
    }

    private MessageHeader toHeader(Map.Entry<String, String> header) {
        if (!ALLOWED_HEADERS.contains(header.getKey())) {
            throw new IllegalArgumentException("허용되지 않은 newsletter email header입니다.");
        }
        rejectLineBreak(header.getKey());
        rejectLineBreak(header.getValue());
        return MessageHeader.builder()
                .name(header.getKey())
                .value(header.getValue())
                .build();
    }

    private Content content(String value) {
        return Content.builder()
                .data(value)
                .charset(StandardCharsets.UTF_8.name())
                .build();
    }

    private String fromAddress() {
        rejectLineBreak(properties.getFromEmail());
        rejectLineBreak(properties.getFromName());
        if (properties.getFromName() == null || properties.getFromName().isBlank()) {
            return properties.getFromEmail();
        }
        return properties.getFromName() + " <" + properties.getFromEmail() + ">";
    }

    private void rejectLineBreak(String value) {
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("email header 값이 올바르지 않습니다.");
        }
    }

    private NewsletterEmailSendException classify(SesV2Exception exception) {
        int statusCode = exception.statusCode();
        String errorCode = safeErrorCode(exception.awsErrorDetails());
        if (statusCode == 401 || statusCode == 403) {
            return new NewsletterEmailSendException(
                    NewsletterFailureType.AUTH_CONFIGURATION,
                    false,
                    true);
        }
        if (TRANSIENT_ERROR_CODES.contains(errorCode)
                || statusCode == 429
                || statusCode >= 500) {
            return new NewsletterEmailSendException(
                    NewsletterFailureType.TRANSIENT_PROVIDER,
                    true,
                    true);
        }
        if (CONFIGURATION_ERROR_CODES.contains(errorCode)) {
            return new NewsletterEmailSendException(
                    NewsletterFailureType.AUTH_CONFIGURATION,
                    false,
                    true);
        }
        return new NewsletterEmailSendException(
                NewsletterFailureType.PERMANENT_REQUEST,
                false,
                true);
    }

    private String safeErrorCode(AwsErrorDetails details) {
        return details == null || details.errorCode() == null
                ? ""
                : details.errorCode();
    }
}
