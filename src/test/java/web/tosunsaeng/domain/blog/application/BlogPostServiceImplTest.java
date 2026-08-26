package web.tosunsaeng.domain.blog.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;
import web.tosunsaeng.domain.blog.exception.BlogPostException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPostServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private BlogPostRepository blogPostRepository;

    private BlogPostServiceImpl blogPostService;

    @BeforeEach
    void setUp() {
        blogPostService = new BlogPostServiceImpl(blogPostRepository, FIXED_CLOCK);
    }

    @Test
    void returnsPublicPostPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        BlogPost post = post("public-post", BlogPostStatus.PUBLISHED, NOW.minusSeconds(60), List.of());
        when(blogPostRepository.findPublicPosts(NOW, pageable))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));

        BlogPostResponseDTO.PostPageResult result = blogPostService.getPublicPosts(0, 10);

        assertThat(result.getPosts()).hasSize(1);
        assertThat(result.getPosts().getFirst().getSlug()).isEqualTo("public-post");
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void returnsPublicPostDetail() {
        BlogPost post = post("public-post", BlogPostStatus.PUBLISHED, NOW.minusSeconds(60), List.of());
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("public-post", NOW))
                .thenReturn(Optional.of(post));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(3)))
                .thenReturn(List.of());

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("public-post");

        assertThat(result.getSlug()).isEqualTo("public-post");
        assertThat(result.getContentMarkdown()).isEqualTo("# public-post");
        assertThat(result.getRelatedPosts()).isEmpty();
        verify(blogPostRepository)
                .findPublicPostBySlugAndIncrementViewCount("public-post", NOW);
        verify(blogPostRepository, never()).findPublicPostBySlug(any(), any());
    }

    @Test
    void rejectsReservedSlugBeforeViewCountIncrement() {
        assertError(
                () -> blogPostService.getPublicPost("search"),
                ErrorStatus._BLOG_POST_NOT_FOUND);

        verifyNoInteractions(blogPostRepository);
    }

    @Test
    void throwsNotFoundForMissingSlug() {
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("missing", NOW))
                .thenReturn(Optional.empty());

        assertError(
                () -> blogPostService.getPublicPost("missing"),
                ErrorStatus._BLOG_POST_NOT_FOUND);
    }

    @Test
    void throwsSameNotFoundWhenRepositoryHidesNonPublicPost() {
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("draft", NOW))
                .thenReturn(Optional.empty());

        assertError(
                () -> blogPostService.getPublicPost("draft"),
                ErrorStatus._BLOG_POST_NOT_FOUND);
    }

    @Test
    void returnsAtMostThreeRelatedPosts() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("a", "b", "c", "d"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW)))
                .thenReturn(List.of(post("d"), post("c"), post("b"), post("a")));

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a", "b", "c");
        verify(blogPostRepository, never()).findLatestPublicPostsExcluding(any(), any(), anyInt());
    }

    @Test
    void excludesCurrentPostFromRequestedRelatedSlugs() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("current", "a"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW))).thenReturn(List.of(post("a")));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(2)))
                .thenReturn(List.of());

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a");
        verify(blogPostRepository).findPublicPostsBySlugs(
                argThat(slugs -> slugs.size() == 1 && slugs.contains("a") && !slugs.contains("current")),
                eq(NOW));
    }

    @Test
    void removesDuplicateRelatedSlugs() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("a", "a", "b", "a"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW)))
                .thenReturn(List.of(post("a"), post("b"), post("b")));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(1)))
                .thenReturn(List.of());

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a", "b");
    }

    @Test
    void preservesConfiguredRelatedSlugOrder() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("a", "b", "c"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW)))
                .thenReturn(List.of(post("c"), post("a"), post("b")));

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a", "b", "c");
    }

    @Test
    void skipsConfiguredPostThatPublicRepositoryDoesNotReturn() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("hidden", "a"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW))).thenReturn(List.of(post("a")));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(2)))
                .thenReturn(List.of());

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a");
    }

    @Test
    void supplementsRelatedPostsWithLatestPublicPosts() {
        BlogPost current = post(
                "current",
                BlogPostStatus.PUBLISHED,
                NOW.minusSeconds(60),
                List.of("a"));
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("current", NOW))
                .thenReturn(Optional.of(current));
        when(blogPostRepository.findPublicPostsBySlugs(any(), eq(NOW))).thenReturn(List.of(post("a")));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(2)))
                .thenReturn(List.of(post("latest-1"), post("latest-2")));

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("current");

        assertThat(slugs(result)).containsExactly("a", "latest-1", "latest-2");
        verify(blogPostRepository).findLatestPublicPostsExcluding(
                argThat(slugs -> slugs.containsAll(List.of("current", "a"))),
                eq(NOW),
                eq(2));
    }

    @Test
    void usesFixedClockForListAndDetailQueries() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.findPublicPosts(NOW, pageable))
                .thenReturn(Page.empty(pageable));

        blogPostService.getPublicPosts(0, 10);

        verify(blogPostRepository).findPublicPosts(NOW, pageable);
    }

    @Test
    void acceptsPostPublishedExactlyAtNowWhenRepositoryReturnsIt() {
        BlogPost boundaryPost = post("boundary", BlogPostStatus.PUBLISHED, NOW, List.of());
        when(blogPostRepository.findPublicPostBySlugAndIncrementViewCount("boundary", NOW))
                .thenReturn(Optional.of(boundaryPost));
        when(blogPostRepository.findLatestPublicPostsExcluding(any(), eq(NOW), eq(3)))
                .thenReturn(List.of());

        BlogPostResponseDTO.PostDetailResult result = blogPostService.getPublicPost("boundary");

        assertThat(result.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void trimsSearchQueryAndReturnsNormalizedQuery() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle("토익스피킹", NOW, pageable))
                .thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostSearchResult result =
                blogPostService.searchPublicPosts("  토익스피킹  ", 0, 10);

        assertThat(result.getQuery()).isEqualTo("토익스피킹");
        verify(blogPostRepository).searchPublicPostsByTitle("토익스피킹", NOW, pageable);
    }

    @Test
    void acceptsTwoUnicodeCodePoints() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle("😀가", NOW, pageable))
                .thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostSearchResult result =
                blogPostService.searchPublicPosts("😀가", 0, 10);

        assertThat(result.getQuery().codePointCount(0, result.getQuery().length())).isEqualTo(2);
    }

    @Test
    void rejectsOneUnicodeCodePoint() {
        assertError(
                () -> blogPostService.searchPublicPosts("😀", 0, 10),
                ErrorStatus._BLOG_SEARCH_QUERY_TOO_SHORT);
        verifyNoInteractions(blogPostRepository);
    }

    @Test
    void rejectsNullSearchQuery() {
        assertError(
                () -> blogPostService.searchPublicPosts(null, 0, 10),
                ErrorStatus._BLOG_SEARCH_QUERY_TOO_SHORT);
        verifyNoInteractions(blogPostRepository);
    }

    @Test
    void acceptsFiftyUnicodeCodePoints() {
        String query = "가".repeat(50);
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle(query, NOW, pageable))
                .thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostSearchResult result =
                blogPostService.searchPublicPosts(query, 0, 10);

        assertThat(result.getQuery()).hasSize(50);
    }

    @Test
    void rejectsFiftyOneUnicodeCodePoints() {
        assertError(
                () -> blogPostService.searchPublicPosts("가".repeat(51), 0, 10),
                ErrorStatus._BLOG_SEARCH_QUERY_TOO_LONG);
        verifyNoInteractions(blogPostRepository);
    }

    @Test
    void passesRegexSpecialCharactersAsLiteralInputToRepository() {
        String query = "토익.*[1]";
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle(query, NOW, pageable))
                .thenReturn(Page.empty(pageable));

        blogPostService.searchPublicPosts(query, 0, 10);

        verify(blogPostRepository).searchPublicPostsByTitle(query, NOW, pageable);
    }

    @Test
    void returnsEmptyPostsForNoSearchResults() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle("없는글", NOW, pageable))
                .thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostSearchResult result =
                blogPostService.searchPublicPosts("없는글", 0, 10);

        assertThat(result.getPosts()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void searchReturnsOnlyWhatPublicRepositoryProvides() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(blogPostRepository.searchPublicPostsByTitle("비공개", NOW, pageable))
                .thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostSearchResult result =
                blogPostService.searchPublicPosts("비공개", 0, 10);

        assertThat(result.getPosts()).isEmpty();
    }

    @Test
    void rejectsNegativePage() {
        assertError(
                () -> blogPostService.getPublicPosts(-1, 10),
                ErrorStatus._BLOG_PAGE_NEGATIVE);
    }

    @Test
    void rejectsSizeBelowOne() {
        assertError(
                () -> blogPostService.getPublicPosts(0, 0),
                ErrorStatus._BLOG_SIZE_TOO_SMALL);
    }

    @Test
    void acceptsSizeOneHundred() {
        PageRequest pageable = PageRequest.of(0, 100);
        when(blogPostRepository.findPublicPosts(NOW, pageable)).thenReturn(Page.empty(pageable));

        BlogPostResponseDTO.PostPageResult result = blogPostService.getPublicPosts(0, 100);

        assertThat(result.getSize()).isEqualTo(100);
    }

    @Test
    void rejectsSizeAboveOneHundred() {
        assertError(
                () -> blogPostService.getPublicPosts(0, 101),
                ErrorStatus._BLOG_SIZE_TOO_LARGE);
    }

    @Test
    void searchUsesSamePaginationPolicyAsList() {
        assertError(
                () -> blogPostService.searchPublicPosts("검색", -1, 10),
                ErrorStatus._BLOG_PAGE_NEGATIVE);
        assertError(
                () -> blogPostService.searchPublicPosts("검색", 0, 0),
                ErrorStatus._BLOG_SIZE_TOO_SMALL);
        assertError(
                () -> blogPostService.searchPublicPosts("검색", 0, 101),
                ErrorStatus._BLOG_SIZE_TOO_LARGE);
        verifyNoInteractions(blogPostRepository);
    }

    private BlogPost post(String slug) {
        return post(slug, BlogPostStatus.PUBLISHED, NOW.minusSeconds(60), List.of());
    }

    private BlogPost post(
            String slug,
            BlogPostStatus status,
            Instant publishedAt,
            List<String> relatedPostSlugs) {
        return BlogPost.builder()
                .id("id-" + slug)
                .slug(slug)
                .title("title-" + slug)
                .summary("summary-" + slug)
                .contentMarkdown("# " + slug)
                .thumbnailUrl("https://example.com/" + slug + ".png")
                .authorName("토선생")
                .status(status)
                .seoTitle("seo-" + slug)
                .seoDescription("seo-description-" + slug)
                .relatedPostSlugs(relatedPostSlugs)
                .publishedAt(publishedAt)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private List<String> slugs(BlogPostResponseDTO.PostDetailResult result) {
        return result.getRelatedPosts().stream()
                .map(BlogPostResponseDTO.RelatedPostSummary::getSlug)
                .toList();
    }

    private void assertError(Runnable action, ErrorStatus expectedStatus) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BlogPostException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expectedStatus));
    }
}
