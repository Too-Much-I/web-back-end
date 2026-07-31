package web.tosunsaeng.domain.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class BlogPostResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostSummary {
        private String slug;
        private String title;
        private String summary;
        private String thumbnailUrl;
        private String authorName;
        private Instant publishedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedPostSummary {
        private String slug;
        private String title;
        private String summary;
        private String thumbnailUrl;
        private Instant publishedAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostPageResult {
        private List<PostSummary> posts;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostSearchResult {
        private String query;
        private List<PostSummary> posts;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostDetailResult {
        private String slug;
        private String title;
        private String summary;
        private String contentMarkdown;
        private String thumbnailUrl;
        private String authorName;
        private String seoTitle;
        private String seoDescription;
        private Instant publishedAt;
        private Instant updatedAt;
        private List<RelatedPostSummary> relatedPosts;
    }
}
