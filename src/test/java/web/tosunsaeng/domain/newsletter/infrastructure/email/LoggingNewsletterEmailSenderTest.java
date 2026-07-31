package web.tosunsaeng.domain.newsletter.infrastructure.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingNewsletterEmailSenderTest {

    @Test
    void simulatesWithoutLoggingRecipientTokenOrBody() {
        Logger logger = (Logger) LoggerFactory.getLogger(
                LoggingNewsletterEmailSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            NewsletterEmailMessage message = new NewsletterEmailMessage(
                    "sensitive@example.test",
                    "sensitive subject",
                    "<p>sensitive-html signed-token</p>",
                    "sensitive-plain signed-token",
                    Map.of("List-Unsubscribe", "<https://api/signed-token>"));

            NewsletterEmailSendResult result =
                    new LoggingNewsletterEmailSender().send(message);

            assertThat(result.providerMessageId()).isEqualTo("logging-simulated");
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly(
                            "Newsletter logging provider accepted a simulated message")
                    .allSatisfy(log -> assertThat(log).doesNotContain(
                            "sensitive@example.test",
                            "signed-token",
                            "sensitive-html",
                            "sensitive-plain",
                            "sensitive subject"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
