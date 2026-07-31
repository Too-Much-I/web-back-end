package web.tosunsaeng.domain.newsletter.config;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.comment.config.AnonymousSessionProperties;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterPropertiesTest {

    private static final String ACTIVE_SECRET =
            "test-only-newsletter-active-secret-at-least-32-bytes";
    private static final String PREVIOUS_SECRET =
            "test-only-newsletter-previous-secret-at-least-32-bytes";
    private static final String RATE_SECRET =
            "test-only-newsletter-rate-secret-at-least-32-bytes";
    private static final String ANONYMOUS_SECRET =
            "test-only-anonymous-token-secret-at-least-32-bytes";
    private static final String COMMENT_SECRET =
            "test-only-comment-rate-limit-secret-at-least-32-bytes";

    @Test
    void tokenPropertiesSupportActiveAndPreviousKeysWithApprovedDefaults() {
        NewsletterUnsubscribeTokenProperties properties = tokenProperties(
                ACTIVE_SECRET,
                Map.of("previous-v0", PREVIOUS_SECRET));

        properties.afterPropertiesSet();

        assertThat(properties.getFormatVersion()).isEqualTo(1);
        assertThat(properties.getMaxFutureSkewSeconds()).isEqualTo(300);
        assertThat(properties.verificationSecrets().keySet())
                .containsExactly("active-v1", "previous-v0");
    }

    @Test
    void tokenPropertiesRejectMissingShortBlankAndDuplicateKeyConfiguration() {
        NewsletterUnsubscribeTokenProperties missing = tokenProperties("", Map.of());
        assertThatThrownBy(missing::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");

        NewsletterUnsubscribeTokenProperties blankKey = tokenProperties(
                ACTIVE_SECRET, Map.of());
        blankKey.setKeyId(" ");
        assertThatThrownBy(blankKey::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keyId");

        NewsletterUnsubscribeTokenProperties duplicateKey = tokenProperties(
                ACTIVE_SECRET, Map.of("active-v1", PREVIOUS_SECRET));
        assertThatThrownBy(duplicateKey::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keyId");

        NewsletterUnsubscribeTokenProperties duplicateSecret = tokenProperties(
                ACTIVE_SECRET, Map.of("previous-v0", ACTIVE_SECRET));
        assertThatThrownBy(duplicateSecret::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다른 secret");
    }

    @Test
    void rateLimitDefaultsAreEnabledAndValidateSecretAndWindowOrder() {
        NewsletterRateLimitProperties properties = rateProperties(RATE_SECRET, true);

        properties.afterPropertiesSet();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getMediumLimit()).isEqualTo(10);
        assertThat(properties.getMediumWindowSeconds()).isEqualTo(600);
        assertThat(properties.getDailyLimit()).isEqualTo(30);
        assertThat(properties.getDailyWindowSeconds()).isEqualTo(86_400);

        assertThatThrownBy(() -> rateProperties("short", true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
        NewsletterRateLimitProperties invalidOrder = rateProperties(RATE_SECRET, true);
        invalidOrder.setDailyLimit(10);
        assertThatThrownBy(invalidOrder::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("daily");
    }

    @Test
    void disabledTestRateLimitDoesNotRequireOperationalSecret() {
        NewsletterRateLimitProperties properties = rateProperties("", false);

        properties.afterPropertiesSet();

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void allNewsletterSecretsMustBeSeparateFromExistingFeatureSecrets() {
        NewsletterConfig config = new NewsletterConfig();
        NewsletterUnsubscribeTokenProperties token = tokenProperties(
                ACTIVE_SECRET, Map.of("previous-v0", PREVIOUS_SECRET));
        token.afterPropertiesSet();
        NewsletterRateLimitProperties rate = rateProperties(RATE_SECRET, true);
        rate.afterPropertiesSet();
        AnonymousSessionProperties anonymous = anonymousProperties(ANONYMOUS_SECRET);
        BlogCommentAbuseProperties comment = commentProperties(COMMENT_SECRET);

        config.newsletterSecretSeparationValidator(
                token, rate, anonymous, comment).afterPropertiesSet();

        NewsletterRateLimitProperties reusedRate = rateProperties(ANONYMOUS_SECRET, true);
        reusedRate.afterPropertiesSet();
        assertThatThrownBy(() -> config.newsletterSecretSeparationValidator(
                        token, reusedRate, anonymous, comment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달라야");

        NewsletterUnsubscribeTokenProperties reusedToken = tokenProperties(
                COMMENT_SECRET, Map.of());
        reusedToken.afterPropertiesSet();
        assertThatThrownBy(() -> config.newsletterSecretSeparationValidator(
                        reusedToken, rate, anonymous, comment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달라야");
    }

    private NewsletterUnsubscribeTokenProperties tokenProperties(
            String secret,
            Map<String, String> previousKeys) {
        NewsletterUnsubscribeTokenProperties properties =
                new NewsletterUnsubscribeTokenProperties();
        properties.setSecret(secret);
        properties.setKeyId("active-v1");
        properties.setPreviousKeys(previousKeys);
        return properties;
    }

    private NewsletterRateLimitProperties rateProperties(
            String secret,
            boolean enabled) {
        NewsletterRateLimitProperties properties = new NewsletterRateLimitProperties();
        properties.setSecret(secret);
        properties.setEnabled(enabled);
        return properties;
    }

    private AnonymousSessionProperties anonymousProperties(String secret) {
        AnonymousSessionProperties properties = new AnonymousSessionProperties();
        properties.setTokenSecret(secret);
        properties.afterPropertiesSet();
        return properties;
    }

    private BlogCommentAbuseProperties commentProperties(String secret) {
        BlogCommentAbuseProperties properties = new BlogCommentAbuseProperties();
        properties.setSecret(secret);
        properties.setEnabled(true);
        properties.afterPropertiesSet();
        return properties;
    }
}
