package web.tosunsaeng.domain.comment.domain.policy;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRateLimitHasherTest {

    @Test
    void sameInputAndSecretAreStableButDifferentSecretsDiffer() {
        CommentRateLimitHasher first = hasher(
                "test-only-comment-rate-limit-secret-number-one-12345");
        CommentRateLimitHasher same = hasher(
                "test-only-comment-rate-limit-secret-number-one-12345");
        CommentRateLimitHasher different = hasher(
                "test-only-comment-rate-limit-secret-number-two-67890");

        String firstHash = first.hashIp("203.0.113.10");

        assertThat(same.hashIp("203.0.113.10")).isEqualTo(firstHash);
        assertThat(different.hashIp("203.0.113.10")).isNotEqualTo(firstHash);
        assertThat(firstHash).matches("[A-Za-z0-9_-]{43}");
        assertThat(firstHash).doesNotContain("203.0.113.10");
    }

    @Test
    void ipAndContentUseSeparatedHmacDomains() {
        CommentRateLimitHasher hasher = hasher(
                "test-only-comment-rate-limit-secret-domain-separation");

        assertThat(hasher.hashIp("same-value"))
                .isNotEqualTo(hasher.hashContent("same-value"));
        assertThat(hasher.hashContent("trimmed content"))
                .doesNotContain("trimmed content");
    }

    private CommentRateLimitHasher hasher(String secret) {
        BlogCommentAbuseProperties properties = new BlogCommentAbuseProperties();
        properties.setSecret(secret);
        properties.afterPropertiesSet();
        return new CommentRateLimitHasher(properties);
    }
}
