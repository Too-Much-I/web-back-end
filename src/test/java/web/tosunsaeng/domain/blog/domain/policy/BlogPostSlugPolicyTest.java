package web.tosunsaeng.domain.blog.domain.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BlogPostSlugPolicyTest {

    @Test
    void acceptsNonBlankNonReservedSlug() {
        assertThat(BlogPostSlugPolicy.isValid("toeic-speaking-guide")).isTrue();
    }

    @Test
    void rejectsNullAndBlankSlug() {
        assertThat(BlogPostSlugPolicy.isValid(null)).isFalse();
        assertThat(BlogPostSlugPolicy.isValid("")).isFalse();
        assertThat(BlogPostSlugPolicy.isValid("   ")).isFalse();
    }

    @Test
    void rejectsSearchReservedSlug() {
        assertThat(BlogPostSlugPolicy.isValid("search")).isFalse();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BlogPostSlugPolicy.validate("search"));
    }

    @Test
    void doesNotTransformSlug() {
        assertThat(BlogPostSlugPolicy.isValid("Search")).isTrue();
        assertThat(BlogPostSlugPolicy.isValid("toeic speaking")).isTrue();
    }
}
