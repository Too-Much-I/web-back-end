package web.tosunsaeng.domain.blog.converter;

import org.springframework.data.domain.Page;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;

import java.util.List;

public final class BlogPostConverter {

    private BlogPostConverter() {
    }

    public static BlogPostResponseDTO.PostPageResult toPostPageResult(Page<BlogPost> page) {
        return BlogPostResponseDTO.PostPageResult.builder()
                .posts(page.getContent().stream()
                        .map(BlogPostConverter::toPostSummary)
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }

    public static BlogPostResponseDTO.PostSearchResult toPostSearchResult(
            String query,
            Page<BlogPost> page) {
        return BlogPostResponseDTO.PostSearchResult.builder()
                .query(query)
                .posts(page.getContent().stream()
                        .map(BlogPostConverter::toPostSummary)
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }

    public static BlogPostResponseDTO.PostDetailResult toPostDetailResult(
            BlogPost post,
            List<BlogPost> relatedPosts) {
        return BlogPostResponseDTO.PostDetailResult.builder()
                .slug(post.getSlug())
                .title(post.getTitle())
                .summary(post.getSummary())
                .contentMarkdown(post.getContentMarkdown())
                .thumbnailUrl(post.getThumbnailUrl())
                .authorName(post.getAuthorName())
                .seoTitle(post.getSeoTitle())
                .seoDescription(post.getSeoDescription())
                .publishedAt(post.getPublishedAt())
                .updatedAt(post.getUpdatedAt())
                .relatedPosts(relatedPosts.stream()
                        .map(BlogPostConverter::toRelatedPostSummary)
                        .toList())
                .build();
    }

    private static BlogPostResponseDTO.PostSummary toPostSummary(BlogPost post) {
        return BlogPostResponseDTO.PostSummary.builder()
                .slug(post.getSlug())
                .title(post.getTitle())
                .summary(post.getSummary())
                .thumbnailUrl(post.getThumbnailUrl())
                .authorName(post.getAuthorName())
                .publishedAt(post.getPublishedAt())
                .build();
    }

    private static BlogPostResponseDTO.RelatedPostSummary toRelatedPostSummary(BlogPost post) {
        return BlogPostResponseDTO.RelatedPostSummary.builder()
                .slug(post.getSlug())
                .title(post.getTitle())
                .summary(post.getSummary())
                .thumbnailUrl(post.getThumbnailUrl())
                .publishedAt(post.getPublishedAt())
                .build();
    }
}
