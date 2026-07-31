package web.tosunsaeng.domain.newsletter.exception;

import lombok.Getter;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.Objects;

@Getter
public class NewsletterRateLimitException extends NewsletterException {

    private final long retryAfterSeconds;
    private final NewsletterRateLimitScope limitScope;

    public NewsletterRateLimitException(
            long retryAfterSeconds,
            NewsletterRateLimitScope limitScope) {
        super(ErrorStatus._NEWSLETTER_SUBSCRIBE_RATE_LIMITED);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds는 1 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
        this.limitScope = Objects.requireNonNull(limitScope);
    }
}
