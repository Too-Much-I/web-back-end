package web.tosunsaeng.domain.blog.comment.application;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import web.tosunsaeng.domain.blog.comment.converter.BlogCommentConverter;
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentSpamPatternPolicy;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.blog.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.blog.comment.exception.CommentValidationException;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogCommentServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String OTTER_KEY = "character-image/example-otter-v1.webp";
    private static final String PENGUIN_KEY = "character-image/example-penguin-v1.webp";

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private BlogCommentRepository blogCommentRepository;

    @Mock
    private AnonymousVisitorService anonymousVisitorService;

    private BlogCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        CommentValidator validator = new CommentValidator(new CommentSpamPatternPolicy());
        BlogCommentConverter converter = new BlogCommentConverter(
                new AvatarImageUrlResolver("https://cdn.example.test"));
        service = new BlogCommentServiceImpl(
                blogPostRepository,
                blogCommentRepository,
                anonymousVisitorService,
                validator,
                converter,
                FIXED_CLOCK);
    }

    @Test
    void returnsVisibleCommentPageUsingPublicPostAndSnapshotImageKey() {
        BlogPost post = publicPost("public-post", NOW.minusSeconds(60));
        PageRequest pageable = PageRequest.of(0, 20);
        BlogComment comment = comment(
                "comment-1",
                "차분한 수달",
                "seed-1",
                OTTER_KEY,
                "정상 댓글",
                NOW.minusSeconds(30));
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(post));
        when(blogCommentRepository.findVisibleCommentsByPostId("post-public-post", pageable))
                .thenReturn(new PageImpl<>(List.of(comment), pageable, 1));

        BlogCommentResponseDTO.CommentPageResult result =
                service.getComments("public-post", 0, 20);

        assertThat(result.getComments()).hasSize(1);
        BlogCommentResponseDTO.CommentSummary summary = result.getComments().getFirst();
        assertThat(summary.getId()).isEqualTo("comment-1");
        assertThat(summary.getNickname()).isEqualTo("차분한 수달");
        assertThat(summary.getAvatarSeed()).isEqualTo("seed-1");
        assertThat(summary.getAvatarImageUrl())
                .isEqualTo("https://cdn.example.test/" + OTTER_KEY);
        assertThat(summary.getContent()).isEqualTo("정상 댓글");
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.isHasNext()).isFalse();
    }

    @Test
    void returnsEmptyCommentPageForPublicPostWithoutComments() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(blogCommentRepository.findVisibleCommentsByPostId("post-public-post", pageable))
                .thenReturn(Page.empty(pageable));

        BlogCommentResponseDTO.CommentPageResult result =
                service.getComments("public-post", 0, 20);

        assertThat(result.getComments()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void nonPublicPostCommentListUsesSameNotFoundAndSkipsCommentQuery() {
        when(blogPostRepository.findPublicPostBySlug("hidden-post", NOW))
                .thenReturn(Optional.empty());

        assertBlogError(
                () -> service.getComments("hidden-post", 0, 20),
                ErrorStatus._BLOG_POST_NOT_FOUND);

        verifyNoInteractions(blogCommentRepository);
    }

    @Test
    void commentListUsesApprovedPaginationPolicy() {
        assertBlogError(
                () -> service.getComments("public-post", -1, 20),
                ErrorStatus._BLOG_PAGE_NEGATIVE);
        assertBlogError(
                () -> service.getComments("public-post", 0, 0),
                ErrorStatus._BLOG_SIZE_TOO_SMALL);
        assertBlogError(
                () -> service.getComments("public-post", 0, 101),
                ErrorStatus._BLOG_SIZE_TOO_LARGE);
        verifyNoInteractions(blogPostRepository, blogCommentRepository);
    }

    @Test
    void commentListAcceptsMaximumSizeOneHundred() {
        PageRequest pageable = PageRequest.of(0, 100);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(blogCommentRepository.findVisibleCommentsByPostId("post-public-post", pageable))
                .thenReturn(Page.empty(pageable));

        BlogCommentResponseDTO.CommentPageResult result =
                service.getComments("public-post", 0, 100);

        assertThat(result.getSize()).isEqualTo(100);
    }

    @Test
    void createsVisibleTrimmedCommentOnlyAfterSuccessfulValidation() {
        BlogPost post = publicPost("public-post", NOW.minusSeconds(60));
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(post));
        when(anonymousVisitorService.resolve("existing-cookie", NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(visitor, "new-cookie"));
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), "comment-1"));
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.textNode("\t 정상 댓글 \n"),
                        "honeypot-is-ignored-in-phase-03");

        BlogCommentService.CreatedCommentSession session =
                service.createComment("public-post", request, "existing-cookie");

        ArgumentCaptor<BlogComment> commentCaptor = ArgumentCaptor.forClass(BlogComment.class);
        verify(blogCommentRepository).save(commentCaptor.capture());
        BlogComment saved = commentCaptor.getValue();
        assertThat(saved.getPostId()).isEqualTo("post-public-post");
        assertThat(saved.getAnonymousVisitorId()).isEqualTo("visitor-1");
        assertThat(saved.getNickname()).isEqualTo("차분한 수달");
        assertThat(saved.getAvatarSeed()).isEqualTo("seed-1");
        assertThat(saved.getAvatarImageKey()).isEqualTo(OTTER_KEY);
        assertThat(saved.getContent()).isEqualTo("정상 댓글");
        assertThat(saved.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getHiddenAt()).isNull();
        assertThat(saved.getHiddenReason()).isNull();

        assertThat(session.rawTokenToSet()).isEqualTo("new-cookie");
        assertThat(session.result().getId()).isEqualTo("comment-1");
        assertThat(session.result().getContent()).isEqualTo("정상 댓글");
        assertThat(session.result().getAvatarImageUrl())
                .isEqualTo("https://cdn.example.test/" + OTTER_KEY);
    }

    @Test
    void validationFailureSavesNeitherVisitorNorComment() {
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.textNode(" \t\n "),
                        "");

        assertThatThrownBy(() -> service.createComment("public-post", request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(1, 8));

        verifyNoInteractions(anonymousVisitorService, blogCommentRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing-post",
            "draft-post",
            "archived-post",
            "future-post",
            "null-published-at-post"
    })
    void everyMissingOrNonPublicPostUsesRuleTenAndSavesNothing(String slug) {
        when(blogPostRepository.findPublicPostBySlug(slug, NOW)).thenReturn(Optional.empty());
        BlogCommentRequestDTO.CreateCommentRequest request = validRequest();

        assertThatThrownBy(() -> service.createComment(slug, request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(10));

        verifyNoInteractions(anonymousVisitorService, blogCommentRepository);
    }

    @Test
    void contentViolationsAndNonPublicPostAreCollectedTogetherInOrder() {
        when(blogPostRepository.findPublicPostBySlug("missing-post", NOW))
                .thenReturn(Optional.empty());
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.nullNode(),
                        "");

        assertThatThrownBy(() -> service.createComment("missing-post", request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(1, 4, 8, 10));

        verifyNoInteractions(anonymousVisitorService, blogCommentRepository);
    }

    @Test
    void fixedClockMakesPublishedAtNowAvailableToCommentCreation() {
        BlogPost boundaryPost = publicPost("boundary-post", NOW);
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        when(blogPostRepository.findPublicPostBySlug("boundary-post", NOW))
                .thenReturn(Optional.of(boundaryPost));
        when(anonymousVisitorService.resolve(null, NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(visitor, "raw-token"));
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), "comment-1"));

        service.createComment("boundary-post", validRequest(), null);

        verify(blogPostRepository).findPublicPostBySlug("boundary-post", NOW);
        verify(anonymousVisitorService).resolve(null, NOW);
    }

    @Test
    void savedCommentProfileSnapshotDoesNotChangeAfterVisitorRegeneration() {
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(anonymousVisitorService.resolve(null, NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(visitor, "raw-token"));
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.createComment("public-post", validRequest(), null);

        ArgumentCaptor<BlogComment> commentCaptor = ArgumentCaptor.forClass(BlogComment.class);
        verify(blogCommentRepository).save(commentCaptor.capture());
        BlogComment snapshot = commentCaptor.getValue();
        visitor.updateProfile("명랑한 펭귄", "seed-2", PENGUIN_KEY, NOW.plusSeconds(60));

        assertThat(snapshot.getNickname()).isEqualTo("차분한 수달");
        assertThat(snapshot.getAvatarSeed()).isEqualTo("seed-1");
        assertThat(snapshot.getAvatarImageKey()).isEqualTo(OTTER_KEY);
    }

    @Test
    void regeneratesAnonymousProfileAndReturnsOnlyResolvedPublicFields() {
        AnonymousVisitor visitor = visitor(
                "visitor-1", "명랑한 펭귄", "new-seed", PENGUIN_KEY);
        when(anonymousVisitorService.regenerate("cookie", NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(visitor, null));

        BlogCommentService.AnonymousProfileSession session =
                service.regenerateAnonymousProfile("cookie");

        assertThat(session.result().getNickname()).isEqualTo("명랑한 펭귄");
        assertThat(session.result().getAvatarSeed()).isEqualTo("new-seed");
        assertThat(session.result().getAvatarImageUrl())
                .isEqualTo("https://cdn.example.test/" + PENGUIN_KEY);
        assertThat(session.rawTokenToSet()).isNull();
    }

    @Test
    void reservedSearchSlugIsNotQueriedAndReturnsRuleTen() {
        assertThatThrownBy(() -> service.createComment("search", validRequest(), null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(10));

        verifyNoInteractions(blogPostRepository, anonymousVisitorService, blogCommentRepository);
    }

    private BlogCommentRequestDTO.CreateCommentRequest validRequest() {
        return new BlogCommentRequestDTO.CreateCommentRequest(
                JsonNodeFactory.instance.textNode("정상 댓글"),
                "");
    }

    private BlogPost publicPost(String slug, Instant publishedAt) {
        return BlogPost.builder()
                .id("post-" + slug)
                .slug(slug)
                .title("제목")
                .summary("요약")
                .contentMarkdown("# 본문")
                .thumbnailUrl("https://example.test/post.webp")
                .authorName("토선생")
                .status(BlogPostStatus.PUBLISHED)
                .seoTitle("SEO")
                .seoDescription("SEO 설명")
                .relatedPostSlugs(List.of())
                .publishedAt(publishedAt)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private AnonymousVisitor visitor(
            String id,
            String nickname,
            String seed,
            String imageKey) {
        return AnonymousVisitor.builder()
                .id(id)
                .tokenHash("token-hash")
                .nickname(nickname)
                .avatarSeed(seed)
                .avatarImageKey(imageKey)
                .createdAt(NOW.minusSeconds(120))
                .lastSeenAt(NOW)
                .build();
    }

    private BlogComment comment(
            String id,
            String nickname,
            String seed,
            String imageKey,
            String content,
            Instant createdAt) {
        return BlogComment.builder()
                .id(id)
                .postId("post-public-post")
                .anonymousVisitorId("visitor-id")
                .nickname(nickname)
                .avatarSeed(seed)
                .avatarImageKey(imageKey)
                .content(content)
                .status(CommentStatus.VISIBLE)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private BlogComment withId(BlogComment source, String id) {
        return BlogComment.builder()
                .id(id)
                .postId(source.getPostId())
                .anonymousVisitorId(source.getAnonymousVisitorId())
                .nickname(source.getNickname())
                .avatarSeed(source.getAvatarSeed())
                .avatarImageKey(source.getAvatarImageKey())
                .content(source.getContent())
                .status(source.getStatus())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .hiddenAt(source.getHiddenAt())
                .hiddenReason(source.getHiddenReason())
                .build();
    }

    private void assertBlogError(Runnable action, ErrorStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BlogCommentException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(expected));
    }
}
