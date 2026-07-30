package web.tosunsaeng.domain.blog.domain.policy;

public final class BlogPostSlugPolicy {

    public static final String SEARCH_RESERVED_SLUG = "search";

    private BlogPostSlugPolicy() {
    }

    public static boolean isValid(String slug) {
        return slug != null
                && !slug.isBlank()
                && !SEARCH_RESERVED_SLUG.equals(slug);
    }

    public static void validate(String slug) {
        if (!isValid(slug)) {
            throw new IllegalArgumentException("게시글 slug는 비어 있을 수 없고 예약어 search를 사용할 수 없습니다.");
        }
    }
}
