package web.tosunsaeng.domain.newsletter.domain.policy;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterRateLimitHasherTest {

    @Test
    void hashesIpDeterministicallyWithoutReturningRawValue() {
        NewsletterRateLimitHasher hasher = new NewsletterRateLimitHasher(properties());

        String first = hasher.hashIp("203.0.113.10");
        String second = hasher.hashIp("203.0.113.10");

        assertThat(first).isEqualTo(second)
                .matches("^[A-Za-z0-9_-]{43}$")
                .doesNotContain("203.0.113.10");
        assertThat(hasher.hashIp("203.0.113.11")).isNotEqualTo(first);
    }

    @Test
    void rejectsMissingClientIpWithoutIncludingItInResult() {
        NewsletterRateLimitHasher hasher = new NewsletterRateLimitHasher(properties());

        assertThatThrownBy(() -> hasher.hashIp(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hashIp(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NewsletterRateLimitProperties properties() {
        NewsletterRateLimitProperties properties = new NewsletterRateLimitProperties();
        properties.setSecret(
                "test-only-newsletter-rate-secret-at-least-32-bytes");
        properties.afterPropertiesSet();
        return properties;
    }
}
