package web.tosunsaeng.domain.blog.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.converter.BlogPostConverter;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.policy.BlogPostSlugPolicy;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;
import web.tosunsaeng.domain.blog.exception.BlogPostException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogPostServiceImpl implements BlogPostService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_RELATED_POSTS = 3;
    static final int MIN_SEARCH_QUERY_CODE_POINTS = 2;
    static final int MAX_SEARCH_QUERY_CODE_POINTS = 50;

    private final BlogPostRepository blogPostRepository;
    private final Clock clock;

    @Override
    public BlogPostResponseDTO.PostPageResult getPublicPosts(int page, int size) {
        validatePagination(page, size);
        Instant now = clock.instant();
        Page<BlogPost> posts = blogPostRepository.findPublicPosts(
                now,
                PageRequest.of(page, size));
        return BlogPostConverter.toPostPageResult(posts);
    }

    @Override
    public BlogPostResponseDTO.PostDetailResult getPublicPost(String slug) {
        if (!BlogPostSlugPolicy.isValid(slug)) {
            throw new BlogPostException(ErrorStatus._BLOG_POST_NOT_FOUND);
        }

        Instant now = clock.instant();
        BlogPost post = blogPostRepository.findPublicPostBySlugAndIncrementViewCount(slug, now)
                .orElseThrow(() -> new BlogPostException(ErrorStatus._BLOG_POST_NOT_FOUND));
        List<BlogPost> relatedPosts = findRelatedPosts(post, now);
        return BlogPostConverter.toPostDetailResult(post, relatedPosts);
    }

    @Override
    public BlogPostResponseDTO.PostSearchResult searchPublicPosts(
            String query,
            int page,
            int size) {
        validatePagination(page, size);
        String normalizedQuery = normalizeSearchQuery(query);
        Instant now = clock.instant();
        Page<BlogPost> posts = blogPostRepository.searchPublicPostsByTitle(
                normalizedQuery,
                now,
                PageRequest.of(page, size));
        return BlogPostConverter.toPostSearchResult(normalizedQuery, posts);
    }

    private List<BlogPost> findRelatedPosts(BlogPost currentPost, Instant now) {
        LinkedHashSet<String> requestedSlugs = normalizeRelatedSlugs(currentPost);
        List<BlogPost> selectedPosts = new ArrayList<>(MAX_RELATED_POSTS);
        Set<String> selectedSlugs = new LinkedHashSet<>();

        if (!requestedSlugs.isEmpty()) {
            List<BlogPost> requestedPosts = blogPostRepository.findPublicPostsBySlugs(
                    requestedSlugs,
                    now);
            Map<String, BlogPost> postsBySlug = requestedPosts.stream()
                    .filter(post -> BlogPostSlugPolicy.isValid(post.getSlug()))
                    .collect(Collectors.toMap(
                            BlogPost::getSlug,
                            Function.identity(),
                            (first, ignored) -> first));

            for (String requestedSlug : requestedSlugs) {
                BlogPost relatedPost = postsBySlug.get(requestedSlug);
                if (relatedPost != null && selectedSlugs.add(requestedSlug)) {
                    selectedPosts.add(relatedPost);
                    if (selectedPosts.size() == MAX_RELATED_POSTS) {
                        return List.copyOf(selectedPosts);
                    }
                }
            }
        }

        int remaining = MAX_RELATED_POSTS - selectedPosts.size();
        Set<String> excludedSlugs = new LinkedHashSet<>();
        excludedSlugs.add(currentPost.getSlug());
        excludedSlugs.addAll(selectedSlugs);

        List<BlogPost> latestPosts = blogPostRepository.findLatestPublicPostsExcluding(
                excludedSlugs,
                now,
                remaining);
        for (BlogPost latestPost : latestPosts) {
            String latestSlug = latestPost.getSlug();
            if (BlogPostSlugPolicy.isValid(latestSlug)
                    && !excludedSlugs.contains(latestSlug)
                    && selectedSlugs.add(latestSlug)) {
                selectedPosts.add(latestPost);
                if (selectedPosts.size() == MAX_RELATED_POSTS) {
                    break;
                }
            }
        }

        return List.copyOf(selectedPosts);
    }

    private LinkedHashSet<String> normalizeRelatedSlugs(BlogPost currentPost) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        Collection<String> relatedPostSlugs = currentPost.getRelatedPostSlugs();
        if (relatedPostSlugs == null) {
            return normalized;
        }

        for (String relatedSlug : relatedPostSlugs) {
            if (BlogPostSlugPolicy.isValid(relatedSlug)
                    && !currentPost.getSlug().equals(relatedSlug)) {
                normalized.add(relatedSlug);
            }
        }
        return normalized;
    }

    private String normalizeSearchQuery(String query) {
        if (query == null) {
            throw new BlogPostException(ErrorStatus._BLOG_SEARCH_QUERY_TOO_SHORT);
        }

        String normalized = query.strip();
        int codePointLength = normalized.codePointCount(0, normalized.length());
        if (codePointLength < MIN_SEARCH_QUERY_CODE_POINTS) {
            throw new BlogPostException(ErrorStatus._BLOG_SEARCH_QUERY_TOO_SHORT);
        }
        if (codePointLength > MAX_SEARCH_QUERY_CODE_POINTS) {
            throw new BlogPostException(ErrorStatus._BLOG_SEARCH_QUERY_TOO_LONG);
        }
        return normalized;
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BlogPostException(ErrorStatus._BLOG_PAGE_NEGATIVE);
        }
        if (size < 1) {
            throw new BlogPostException(ErrorStatus._BLOG_SIZE_TOO_SMALL);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new BlogPostException(ErrorStatus._BLOG_SIZE_TOO_LARGE);
        }
    }
}
