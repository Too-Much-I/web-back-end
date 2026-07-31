package web.tosunsaeng.domain.newsletter.domain.sender;

import java.util.Objects;

public record NewsletterEmailSendResult(String providerMessageId) {

    public NewsletterEmailSendResult {
        Objects.requireNonNull(providerMessageId);
    }
}
