package web.tosunsaeng.domain.newsletter.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "newsletter.delivery",
        name = "email-provider",
        havingValue = "logging",
        matchIfMissing = true)
public class LoggingNewsletterEmailSender implements NewsletterEmailSender {

    @Override
    public NewsletterEmailSendResult send(NewsletterEmailMessage message) {
        log.info("Newsletter logging provider accepted a simulated message");
        return new NewsletterEmailSendResult("logging-simulated");
    }
}
