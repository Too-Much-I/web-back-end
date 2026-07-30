package web.tosunsaeng.domain.blog.comment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import web.tosunsaeng.domain.blog.comment.converter.BlogCommentModerationConverter;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.blog.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.blog.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentModerationDTO;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
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
class BlogCommentModerationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-30T02:00:00Z");
    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");

    @Mock
    private BlogCommentRepository commentRepository;

    @Mock
    private BlogPostRepository postRepository;

    private BlogCommentModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        BlogCommentModerationConverter converter = new BlogCommentModerationConverter(
                new AvatarImageUrlResolver("https://cdn.example.test"));
        service = new BlogCommentModerationServiceImpl(
                commentRepository,
                postRepository,
                converter,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsByStatusPostPeriodAndPaginationWithoutSensitiveVisitorData() {
        PageRequest pageable = PageRequest.of(1, 20);
        BlogComment comment = comment(CommentStatus.HIDDEN, HiddenReason.SPAM);
        when(commentRepository.findCommentsForModeration(
                CommentStatus.HIDDEN, "post-id", FROM, TO, pageable))
                .thenReturn(new PageImpl<>(List.of(comment), pageable, 21));

        BlogCommentModerationDTO.ModeratedCommentPageResult result =
                service.getComments(filter(
                        CommentStatus.HIDDEN,
                        "post-id",
                        null,
                        FROM,
                        TO,
                        1,
                        20));

        assertThat(result.getTotalElements()).isEqualTo(21);
        assertThat(result.isHasNext()).isFalse();
        BlogCommentModerationDTO.ModeratedCommentResult item =
                result.getComments().getFirst();
        assertThat(item.getId()).isEqualTo("comment-id");
        assertThat(item.getPostId()).isEqualTo("post-id");
        assertThat(item.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(item.getHiddenReason()).isEqualTo(HiddenReason.SPAM);
        assertThat(item.getAvatarImageUrl())
                .isEqualTo("https://cdn.example.test/character-image/otter.webp");
        assertThat(List.of(item.getClass().getDeclaredFields()).stream()
                .map(java.lang.reflect.Field::getName))
                .doesNotContain(
                        "anonymousVisitorId",
                        "tokenHash",
                        "ipHash",
                        "reservationOwner");
    }

    @Test
    void slugResolvesInternalPostRegardlessOfPublicationStatus() {
        BlogPost draft = BlogPost.builder()
                .id("draft-post-id")
                .slug("draft-post")
                .status(BlogPostStatus.DRAFT)
                .build();
        PageRequest pageable = PageRequest.of(0, 10);
        when(postRepository.findBySlug("draft-post")).thenReturn(Optional.of(draft));
        when(commentRepository.findCommentsForModeration(
                null, "draft-post-id", null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getComments(filter(null, null, "draft-post", null, null, 0, 10));

        verify(postRepository).findBySlug("draft-post");
        verify(commentRepository).findCommentsForModeration(
                null, "draft-post-id", null, null, pageable);
    }

    @Test
    void missingSlugReturnsEmptyPageWithoutCommentQuery() {
        when(postRepository.findBySlug("missing-post")).thenReturn(Optional.empty());

        BlogCommentModerationDTO.ModeratedCommentPageResult result = service.getComments(
                filter(null, null, "missing-post", null, null, 2, 20));

        assertThat(result.getComments()).isEmpty();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        verifyNoInteractions(commentRepository);
    }

    @Test
    void rejectsAmbiguousPostFilterInvalidPeriodAndPagination() {
        assertInvalid(filter(null, "post-id", "slug", null, null, 0, 20));
        assertInvalid(filter(null, null, null, TO, TO, 0, 20));
        assertInvalid(filter(null, null, null, TO, FROM, 0, 20));
        assertInvalid(filter(null, null, null, null, null, -1, 20));
        assertInvalid(filter(null, null, null, null, null, 0, 0));
        assertInvalid(filter(null, null, null, null, null, 0, 101));

        verifyNoInteractions(commentRepository, postRepository);
    }

    @Test
    void hideAndRestoreUseClockAndReturnUpdatedDocument() {
        BlogComment hidden = comment(CommentStatus.HIDDEN, HiddenReason.ABUSE);
        BlogComment visible = comment(CommentStatus.VISIBLE, null);
        when(commentRepository.hideComment("comment-id", HiddenReason.ABUSE, NOW))
                .thenReturn(Optional.of(hidden));
        when(commentRepository.restoreComment("comment-id", NOW))
                .thenReturn(Optional.of(visible));

        var hideResult = service.hide("comment-id", HiddenReason.ABUSE);
        var restoreResult = service.restore("comment-id");

        assertThat(hideResult.getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(hideResult.getHiddenReason()).isEqualTo(HiddenReason.ABUSE);
        assertThat(restoreResult.getStatus()).isEqualTo(CommentStatus.VISIBLE);
        assertThat(restoreResult.getHiddenReason()).isNull();
        verify(commentRepository).hideComment("comment-id", HiddenReason.ABUSE, NOW);
        verify(commentRepository).restoreComment("comment-id", NOW);
    }

    @Test
    void hideRequiresReasonAndBothOperationsRequireCommentId() {
        assertError(
                () -> service.hide("comment-id", null),
                ErrorStatus._COMMENT_HIDDEN_REASON_REQUIRED);
        assertError(
                () -> service.hide(" ", HiddenReason.SPAM),
                ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
        assertError(
                () -> service.restore(null),
                ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);

        verifyNoInteractions(commentRepository);
    }

    @Test
    void conditionalTransitionMissDistinguishesNotFoundFromStateConflict() {
        when(commentRepository.hideComment("missing", HiddenReason.SPAM, NOW))
                .thenReturn(Optional.empty());
        when(commentRepository.findById("missing")).thenReturn(Optional.empty());
        assertError(
                () -> service.hide("missing", HiddenReason.SPAM),
                ErrorStatus._COMMENT_NOT_FOUND);

        BlogComment hidden = comment(CommentStatus.HIDDEN, HiddenReason.SPAM);
        when(commentRepository.restoreComment("visible", NOW)).thenReturn(Optional.empty());
        when(commentRepository.findById("visible")).thenReturn(Optional.of(hidden));
        assertError(
                () -> service.restore("visible"),
                ErrorStatus._COMMENT_STATE_CONFLICT);
    }

    private BlogCommentModerationDTO.CommentFilter filter(
            CommentStatus status,
            String postId,
            String slug,
            Instant from,
            Instant to,
            int page,
            int size) {
        return BlogCommentModerationDTO.CommentFilter.builder()
                .status(status)
                .postId(postId)
                .slug(slug)
                .createdAtFrom(from)
                .createdAtTo(to)
                .page(page)
                .size(size)
                .build();
    }

    private BlogComment comment(CommentStatus status, HiddenReason hiddenReason) {
        return BlogComment.builder()
                .id("comment-id")
                .postId("post-id")
                .anonymousVisitorId("visitor-secret-id")
                .nickname("차분한 수달")
                .avatarSeed("seed")
                .avatarImageKey("character-image/otter.webp")
                .content("댓글 본문")
                .status(status)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .hiddenAt(status == CommentStatus.HIDDEN ? NOW : null)
                .hiddenReason(hiddenReason)
                .build();
    }

    private void assertInvalid(BlogCommentModerationDTO.CommentFilter filter) {
        assertError(
                () -> service.getComments(filter),
                ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
    }

    private void assertError(Runnable action, ErrorStatus status) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BlogCommentException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(status));
    }
}
