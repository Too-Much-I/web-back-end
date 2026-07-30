package web.tosunsaeng.domain.blog.comment.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentRateLimitKeyFactoryTest {

    private final CommentRateLimitKeyFactory factory = new CommentRateLimitKeyFactory();

    @Test
    void createsOnlyApprovedBlogCommentNamespaceKeys() {
        CommentRateLimitKeyFactory.RateLimitKeys keys = factory.create(
                "visitor_hash",
                "ip_hash",
                "post-id",
                "content_hash");

        assertThat(keys.asList()).containsExactly(
                "blog:comment:rate:visitor:short:visitor_hash",
                "blog:comment:rate:visitor:medium:visitor_hash",
                "blog:comment:rate:visitor:daily:visitor_hash",
                "blog:comment:rate:ip:medium:ip_hash",
                "blog:comment:rate:ip:daily:ip_hash",
                "blog:comment:duplicate:visitor_hash:post-id:content_hash");
        assertThat(keys.asList())
                .allMatch(key -> key.startsWith("blog:comment:"))
                .noneMatch(key -> key.startsWith("exam:status:"));
    }

    @Test
    void rejectsUnsafeSegmentsThatCouldExposeOrReshapeKeys() {
        assertThatThrownBy(() -> factory.create(
                "raw:token",
                "ip_hash",
                "post-id",
                "content_hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(
                "visitor_hash",
                "203.0.113.10",
                "post-id",
                "content_hash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.create(
                "visitor_hash",
                "ip_hash",
                "post-id",
                "comment@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void duplicateIdentityChangesForDifferentVisitorPostOrContent() {
        String baseline = factory.create(
                "visitor_a", "ip_hash", "post-a", "content_a").duplicate();

        assertThat(factory.create(
                        "visitor_b", "ip_hash", "post-a", "content_a").duplicate())
                .isNotEqualTo(baseline);
        assertThat(factory.create(
                        "visitor_a", "ip_hash", "post-b", "content_a").duplicate())
                .isNotEqualTo(baseline);
        assertThat(factory.create(
                        "visitor_a", "ip_hash", "post-a", "content_b").duplicate())
                .isNotEqualTo(baseline);
    }
}
