package web.tosunsaeng.domain.blog.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BlogPostQueryRepository {

    Page<BlogPost> findPublicPosts(Instant now, Pageable pageable);

    Optional<BlogPost> findPublicPostBySlug(String slug, Instant now);

    Optional<BlogPost> findPublicPostBySlugAndIncrementViewCount(String slug, Instant now);

    Page<BlogPost> searchPublicPostsByTitle(String query, Instant now, Pageable pageable);

    List<BlogPost> findPublicPostsBySlugs(Collection<String> slugs, Instant now);

    List<BlogPost> findLatestPublicPostsExcluding(
            Collection<String> excludedSlugs,
            Instant now,
            int limit);

    List<BlogPost> findNewsletterEligiblePostsAfter(String lastSeenId, int limit);
}
