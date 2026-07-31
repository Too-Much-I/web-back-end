package web.tosunsaeng.domain.newsletter.domain.policy;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class NewsletterRetryPolicy {

    public static final int MAX_PROVIDER_ATTEMPTS = 4;

    public Optional<Instant> nextRetryAt(int failedAttemptCount, Instant failedAt) {
        Duration delay = switch (failedAttemptCount) {
            case 1 -> Duration.ofMinutes(5);
            case 2 -> Duration.ofMinutes(30);
            case 3 -> Duration.ofHours(2);
            default -> null;
        };
        return delay == null
                ? Optional.empty()
                : Optional.of(failedAt.plus(delay));
    }
}
