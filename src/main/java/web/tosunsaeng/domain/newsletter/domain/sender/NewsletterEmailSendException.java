package web.tosunsaeng.domain.newsletter.domain.sender;

import lombok.Getter;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.util.Objects;

@Getter
public class NewsletterEmailSendException extends RuntimeException {

    private final NewsletterFailureType failureType;
    private final boolean retryable;
    private final boolean providerResultKnown;

    public NewsletterEmailSendException(
            NewsletterFailureType failureType,
            boolean retryable,
            boolean providerResultKnown) {
        super("newsletter provider 요청을 처리할 수 없습니다.");
        this.failureType = Objects.requireNonNull(failureType);
        this.retryable = retryable;
        this.providerResultKnown = providerResultKnown;
    }
}
