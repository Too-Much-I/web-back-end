package web.tosunsaeng.domain.comment.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlogCommentAbusePropertiesTest {

    private static final String RATE_SECRET =
            "test-only-comment-rate-limit-secret-at-least-32-bytes";
    private static final String TOKEN_SECRET =
            "test-only-anonymous-token-secret-at-least-32-bytes";

    @Test
    void approvedDefaultsAreEnabledAndValidWithSeparateSecret() {
        BlogCommentAbuseProperties properties = properties(RATE_SECRET, true);

        properties.afterPropertiesSet();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getDuplicateTtlSeconds()).isEqualTo(600);
        assertThat(properties.getVisitorShortLimit()).isEqualTo(1);
        assertThat(properties.getVisitorShortWindowSeconds()).isEqualTo(10);
        assertThat(properties.getVisitorMediumLimit()).isEqualTo(5);
        assertThat(properties.getVisitorMediumWindowSeconds()).isEqualTo(600);
        assertThat(properties.getVisitorDailyLimit()).isEqualTo(20);
        assertThat(properties.getVisitorDailyWindowSeconds()).isEqualTo(86_400);
        assertThat(properties.getIpMediumLimit()).isEqualTo(10);
        assertThat(properties.getIpDailyLimit()).isEqualTo(50);
    }

    @Test
    void enabledConfigurationRejectsMissingOrShortSecret() {
        assertThatThrownBy(() -> properties("", true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
        assertThatThrownBy(() -> properties("short", true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 byte");
    }

    @Test
    void disabledTestConfigurationDoesNotRequireOperationalSecret() {
        BlogCommentAbuseProperties properties = properties("", false);

        properties.afterPropertiesSet();

        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void rejectsNonPositiveAndOutOfOrderLimitWindows() {
        BlogCommentAbuseProperties nonPositive = properties(RATE_SECRET, true);
        nonPositive.setDuplicateTtlSeconds(0);
        assertThatThrownBy(nonPositive::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);

        BlogCommentAbuseProperties invalidVisitorOrder = properties(RATE_SECRET, true);
        invalidVisitorOrder.setVisitorMediumWindowSeconds(10);
        assertThatThrownBy(invalidVisitorOrder::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("visitor window");

        BlogCommentAbuseProperties invalidIpOrder = properties(RATE_SECRET, true);
        invalidIpOrder.setIpDailyLimit(10);
        assertThatThrownBy(invalidIpOrder::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IP limit");
    }

    @Test
    void enabledConfigurationRejectsAnonymousTokenSecretReuse() throws Exception {
        BlogCommentAbuseConfig config = new BlogCommentAbuseConfig();
        BlogCommentAbuseProperties abuse = properties(TOKEN_SECRET, true);
        abuse.afterPropertiesSet();
        AnonymousSessionProperties anonymous = anonymousProperties(TOKEN_SECRET);

        assertThatThrownBy(() -> config.blogCommentSecretSeparationValidator(
                        abuse, anonymous).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("달라야");
    }

    @Test
    void distinctSecretsPassSeparationValidation() throws Exception {
        BlogCommentAbuseConfig config = new BlogCommentAbuseConfig();
        BlogCommentAbuseProperties abuse = properties(RATE_SECRET, true);
        abuse.afterPropertiesSet();
        AnonymousSessionProperties anonymous = anonymousProperties(TOKEN_SECRET);

        config.blogCommentSecretSeparationValidator(abuse, anonymous).afterPropertiesSet();
    }

    private BlogCommentAbuseProperties properties(String secret, boolean enabled) {
        BlogCommentAbuseProperties properties = new BlogCommentAbuseProperties();
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
}
