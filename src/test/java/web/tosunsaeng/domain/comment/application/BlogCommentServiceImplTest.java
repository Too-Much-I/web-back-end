package web.tosunsaeng.domain.comment.application;

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
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.comment.converter.BlogCommentConverter;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.comment.domain.policy.CommentSpamPatternPolicy;
import web.tosunsaeng.domain.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.comment.exception.CommentValidationException;
import web.tosunsaeng.domain.comment.exception.CommentRateLimitException;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
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

    @Mock
    private CommentAbusePreventionService commentAbusePreventionService;

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
                commentAbusePreventionService,
                validator,
                converter,
                FIXED_CLOCK);
        lenient().when(commentAbusePreventionService.admit(
                        any(), any(), any(), any()))
                .thenReturn(CommentAbusePreventionService.Admission.withoutReservation());
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
        stubVisitorResolution("existing-cookie", visitor, "new-cookie", true);
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), "comment-1"));
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.textNode("\t 정상 댓글 \n"),
                        " \t ");

        BlogCommentService.CreatedCommentSession session =
                createComment("public-post", request, "existing-cookie");

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
        verify(commentAbusePreventionService).admit(
                eq("token-hash"),
                eq("post-public-post"),
                eq("정상 댓글"),
                any());
        verify(commentAbusePreventionService, never()).releaseDuplicate(any());
    }

    @Test
    void honeypotIsAcceptedWithoutValidationIpRedisVisitorCommentOrCookie() {
        AtomicBoolean ipRequested = new AtomicBoolean();
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.textNode("무시되는 내용"),
                        "  bot-site.example  ");

        BlogCommentService.CreatedCommentSession session = service.createComment(
                "public-post",
                request,
                null,
                () -> {
                    ipRequested.set(true);
                    return "203.0.113.10";
                });

        assertThat(session.acceptedWithoutCreation()).isTrue();
        assertThat(session.result()).isNull();
        assertThat(session.rawTokenToSet()).isNull();
        assertThat(ipRequested).isFalse();
        verifyNoInteractions(
                blogPostRepository,
                anonymousVisitorService,
                blogCommentRepository,
                commentAbusePreventionService);
    }

    @Test
    void rateLimitFailureDoesNotCommitVisitorOrSaveComment() {
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        AnonymousVisitorService.PreparedVisitor prepared =
                new AnonymousVisitorService.PreparedVisitor(visitor, "new-cookie", true);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(anonymousVisitorService.prepare(null, NOW)).thenReturn(prepared);
        when(commentAbusePreventionService.admit(
                eq("token-hash"),
                eq("post-public-post"),
                eq("정상 댓글"),
                any()))
                .thenThrow(new CommentRateLimitException(
                        590,
                        CommentLimitScope.DUPLICATE));

        assertThatThrownBy(() -> createComment("public-post", validRequest(), null))
                .isInstanceOf(CommentRateLimitException.class);

        verify(anonymousVisitorService, never()).commit(any(), any());
        verifyNoInteractions(blogCommentRepository);
        verify(commentAbusePreventionService, never()).releaseDuplicate(any());
    }

    @Test
    void mongoFailureReleasesOnlyCurrentDuplicateReservation() {
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        stubVisitorResolution(null, visitor, "raw-token", true);
        CommentAbusePreventionService.Admission admission =
                CommentAbusePreventionService.Admission.reserved(
                        "duplicate-key",
                        "owner");
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(commentAbusePreventionService.admit(any(), any(), any(), any()))
                .thenReturn(admission);
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenThrow(new IllegalStateException("mongo unavailable"));

        assertThatThrownBy(() -> createComment("public-post", validRequest(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("mongo unavailable");

        verify(commentAbusePreventionService).releaseDuplicate(admission);
    }

    @Test
    void newVisitorTokenCollisionReleasesReservationAndRetriesAdmission() {
        AnonymousVisitor first = visitor(
                null, "차분한 수달", "seed-1", OTTER_KEY);
        AnonymousVisitor second = AnonymousVisitor.builder()
                .id("visitor-2")
                .tokenHash("token-hash-2")
                .nickname("명랑한 펭귄")
                .avatarSeed("seed-2")
                .avatarImageKey(PENGUIN_KEY)
                .createdAt(NOW)
                .lastSeenAt(NOW)
                .build();
        AnonymousVisitorService.PreparedVisitor firstPrepared =
                new AnonymousVisitorService.PreparedVisitor(first, "raw-1", true);
        AnonymousVisitorService.PreparedVisitor secondPrepared =
                new AnonymousVisitorService.PreparedVisitor(second, "raw-2", true);
        CommentAbusePreventionService.Admission firstAdmission =
                CommentAbusePreventionService.Admission.reserved("dup-1", "owner-1");
        CommentAbusePreventionService.Admission secondAdmission =
                CommentAbusePreventionService.Admission.reserved("dup-2", "owner-2");
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(anonymousVisitorService.prepare(null, NOW))
                .thenReturn(firstPrepared, secondPrepared);
        when(commentAbusePreventionService.admit(any(), any(), any(), any()))
                .thenReturn(firstAdmission, secondAdmission);
        when(anonymousVisitorService.commit(firstPrepared, NOW))
                .thenThrow(new DuplicateKeyException("token collision"));
        when(anonymousVisitorService.commit(secondPrepared, NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(second, "raw-2"));
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), "comment-2"));

        BlogCommentService.CreatedCommentSession result =
                createComment("public-post", validRequest(), null);

        assertThat(result.rawTokenToSet()).isEqualTo("raw-2");
        verify(commentAbusePreventionService).releaseDuplicate(firstAdmission);
        verify(commentAbusePreventionService, never()).releaseDuplicate(secondAdmission);
        verify(commentAbusePreventionService, times(2))
                .admit(any(), any(), any(), any());
    }

    @Test
    void tokenCollisionRetriesAreBoundedWithoutPreparingUnusedCandidate() {
        AnonymousVisitor visitor = visitor(
                null, "차분한 수달", "seed-1", OTTER_KEY);
        AnonymousVisitorService.PreparedVisitor prepared =
                new AnonymousVisitorService.PreparedVisitor(visitor, "raw-token", true);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        when(anonymousVisitorService.prepare(null, NOW)).thenReturn(prepared);
        when(anonymousVisitorService.commit(prepared, NOW))
                .thenThrow(new DuplicateKeyException("token collision"));

        assertBlogError(
                () -> createComment("public-post", validRequest(), null),
                ErrorStatus._INTERNAL_SERVER_ERROR);

        verify(anonymousVisitorService, times(5)).prepare(null, NOW);
        verify(anonymousVisitorService, times(5)).commit(prepared, NOW);
        verify(commentAbusePreventionService, times(5)).releaseDuplicate(any());
    }

    @Test
    void validationFailureSavesNeitherVisitorNorComment() {
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.textNode(" \t\n "),
                        "");

        assertThatThrownBy(() -> createComment("public-post", request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(1, 8));

        verifyNoInteractions(
                blogPostRepository,
                anonymousVisitorService,
                blogCommentRepository,
                commentAbusePreventionService);
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

        assertThatThrownBy(() -> createComment(slug, request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(10));

        verifyNoInteractions(anonymousVisitorService, blogCommentRepository);
    }

    @Test
    void contentViolationsAreReturnedBeforePublicPostLookup() {
        BlogCommentRequestDTO.CreateCommentRequest request =
                new BlogCommentRequestDTO.CreateCommentRequest(
                        JsonNodeFactory.instance.nullNode(),
                        "");

        assertThatThrownBy(() -> createComment("missing-post", request, null))
                .isInstanceOfSatisfying(
                        CommentValidationException.class,
                        exception -> assertThat(exception.getViolations().stream()
                                .map(CommentValidator.Violation::ruleNumber)
                                .toList())
                                .containsExactly(1, 4, 8));

        verifyNoInteractions(
                blogPostRepository,
                anonymousVisitorService,
                blogCommentRepository,
                commentAbusePreventionService);
    }

    @Test
    void fixedClockMakesPublishedAtNowAvailableToCommentCreation() {
        BlogPost boundaryPost = publicPost("boundary-post", NOW);
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        when(blogPostRepository.findPublicPostBySlug("boundary-post", NOW))
                .thenReturn(Optional.of(boundaryPost));
        stubVisitorResolution(null, visitor, "raw-token", true);
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), "comment-1"));

        createComment("boundary-post", validRequest(), null);

        verify(blogPostRepository).findPublicPostBySlug("boundary-post", NOW);
        verify(anonymousVisitorService).prepare(null, NOW);
    }

    @Test
    void savedCommentProfileSnapshotDoesNotChangeAfterVisitorRegeneration() {
        AnonymousVisitor visitor = visitor(
                "visitor-1", "차분한 수달", "seed-1", OTTER_KEY);
        when(blogPostRepository.findPublicPostBySlug("public-post", NOW))
                .thenReturn(Optional.of(publicPost("public-post", NOW.minusSeconds(60))));
        stubVisitorResolution(null, visitor, "raw-token", true);
        when(blogCommentRepository.save(any(BlogComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        createComment("public-post", validRequest(), null);

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
        assertThatThrownBy(() -> createComment("search", validRequest(), null))
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

    private BlogCommentService.CreatedCommentSession createComment(
            String slug,
            BlogCommentRequestDTO.CreateCommentRequest request,
            String rawToken) {
        return service.createComment(slug, request, rawToken, () -> "127.0.0.1");
    }

    private void stubVisitorResolution(
            String rawToken,
            AnonymousVisitor visitor,
            String rawTokenToSet,
            boolean newVisitor) {
        AnonymousVisitorService.PreparedVisitor prepared =
                new AnonymousVisitorService.PreparedVisitor(
                        visitor,
                        rawTokenToSet,
                        newVisitor);
        when(anonymousVisitorService.prepare(rawToken, NOW)).thenReturn(prepared);
        when(anonymousVisitorService.commit(prepared, NOW))
                .thenReturn(new AnonymousVisitorService.VisitorResolution(
                        visitor,
                        rawTokenToSet));
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
