package web.tosunsaeng.domain.newsletter.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterRateLimitKeyFactoryTest {

    @Test
    void createsNewsletterNamespaceWithoutRawPersonalInformation() {
        NewsletterRateLimitKeyFactory.RateLimitKeys keys =
                new NewsletterRateLimitKeyFactory().create("safe_ip_hash");

        assertThat(keys.medium())
                .isEqualTo("newsletter:subscribe:rate:ip:medium:safe_ip_hash");
        assertThat(keys.daily())
                .isEqualTo("newsletter:subscribe:rate:ip:daily:safe_ip_hash");
        assertThat(keys.asList()).allMatch(key ->
                !key.startsWith("exam:status:")
                        && !key.startsWith("blog:comment:"));
    }

    @Test
    void rejectsUnsafeKeySegments() {
        NewsletterRateLimitKeyFactory factory = new NewsletterRateLimitKeyFactory();

        assertThatThrownBy(() -> factory.create("203.0.113.10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create("hash:email@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
