package web.tosunsaeng.domain.newsletter.domain.sender;

import java.util.Map;
import java.util.Objects;

public final class NewsletterEmailMessage {

    private final String recipient;
    private final String subject;
    private final String htmlBody;
    private final String plainTextBody;
    private final Map<String, String> headers;

    public NewsletterEmailMessage(
            String recipient,
            String subject,
            String htmlBody,
            String plainTextBody,
            Map<String, String> headers) {
        this.recipient = Objects.requireNonNull(recipient);
        this.subject = Objects.requireNonNull(subject);
        this.htmlBody = Objects.requireNonNull(htmlBody);
        this.plainTextBody = Objects.requireNonNull(plainTextBody);
        this.headers = Map.copyOf(Objects.requireNonNull(headers));
    }

    public String recipient() {
        return recipient;
    }

    public String subject() {
        return subject;
    }

    public String htmlBody() {
        return htmlBody;
    }

    public String plainTextBody() {
        return plainTextBody;
    }

    public Map<String, String> headers() {
        return headers;
    }

    @Override
    public String toString() {
        return "NewsletterEmailMessage[redacted]";
    }
}
