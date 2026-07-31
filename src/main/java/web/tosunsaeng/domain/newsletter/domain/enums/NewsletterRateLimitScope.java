package web.tosunsaeng.domain.newsletter.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NewsletterRateLimitScope {
    IP_DAILY(1),
    IP_MEDIUM(2);

    private final int tiePriority;

}
