package web.tosunsaeng.domain.blog.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.policy.BlogPostSlugPolicy;

import java.time.Instant;
import java.util.List;

@Getter
@Document(collection = "blog_posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogPost {

    @Id
    private String id;
    private String slug;
    private String title;
    private String summary;
    private String contentMarkdown;
    private String thumbnailUrl;
    private String authorName;
    private BlogPostStatus status;
    private String seoTitle;
    private String seoDescription;
    private List<String> relatedPostSlugs;
    private Instant publishedAt;
    private boolean newsletterEnabled;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder
    public BlogPost(
            String id,
            String slug,
            String title,
            String summary,
            String contentMarkdown,
            String thumbnailUrl,
            String authorName,
            BlogPostStatus status,
            String seoTitle,
            String seoDescription,
            List<String> relatedPostSlugs,
            Instant publishedAt,
            boolean newsletterEnabled,
            Instant createdAt,
            Instant updatedAt) {
        BlogPostSlugPolicy.validate(slug);
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.contentMarkdown = contentMarkdown;
        this.thumbnailUrl = thumbnailUrl;
        this.authorName = authorName;
        this.status = status;
        this.seoTitle = seoTitle;
        this.seoDescription = seoDescription;
        this.relatedPostSlugs = relatedPostSlugs;
        this.publishedAt = publishedAt;
        this.newsletterEnabled = newsletterEnabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
