package web.tosunsaeng.domain.comment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.comment.converter.BlogCommentModerationConverter;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.comment.domain.repository.BlogCommentRepository;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BlogCommentModerationServiceImpl implements BlogCommentModerationService {

    static final int MAX_PAGE_SIZE = 100;

    private final BlogCommentRepository blogCommentRepository;
    private final BlogPostRepository blogPostRepository;
    private final BlogCommentModerationConverter converter;
    private final Clock clock;

    @Override
    public BlogCommentModerationDTO.ModeratedCommentPageResult getComments(
            BlogCommentModerationDTO.CommentFilter filter) {
        validateFilter(filter);
        PageRequest pageable = PageRequest.of(filter.getPage(), filter.getSize());
        String postId = normalize(filter.getPostId());
        String slug = normalize(filter.getSlug());
        if (slug != null) {
            var post = blogPostRepository.findBySlug(slug);
            if (post.isEmpty()) {
                return converter.toPageResult(Page.empty(pageable));
            }
            postId = post.get().getId();
        }

        Page<BlogComment> comments = blogCommentRepository.findCommentsForModeration(
                filter.getStatus(),
                postId,
                filter.getCreatedAtFrom(),
                filter.getCreatedAtTo(),
                pageable);
        return converter.toPageResult(comments);
    }

    @Override
    public BlogCommentModerationDTO.ModeratedCommentResult hide(
            String commentId,
            HiddenReason reason) {
        validateCommentId(commentId);
        if (reason == null) {
            throw new BlogCommentException(ErrorStatus._COMMENT_HIDDEN_REASON_REQUIRED);
        }
        Instant now = clock.instant();
        BlogComment comment = blogCommentRepository.hideComment(commentId, reason, now)
                .orElseGet(() -> throwTransitionFailure(commentId));
        return converter.toResult(comment);
    }

    @Override
    public BlogCommentModerationDTO.ModeratedCommentResult restore(String commentId) {
        validateCommentId(commentId);
        Instant now = clock.instant();
        BlogComment comment = blogCommentRepository.restoreComment(commentId, now)
                .orElseGet(() -> throwTransitionFailure(commentId));
        return converter.toResult(comment);
    }

    private void validateFilter(BlogCommentModerationDTO.CommentFilter filter) {
        if (filter == null
                || filter.getPage() < 0
                || filter.getSize() < 1
                || filter.getSize() > MAX_PAGE_SIZE) {
            throw new BlogCommentException(ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
        }
        String postId = normalize(filter.getPostId());
        String slug = normalize(filter.getSlug());
        if (postId != null && slug != null) {
            throw new BlogCommentException(ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
        }
        if (filter.getCreatedAtFrom() != null
                && filter.getCreatedAtTo() != null
                && !filter.getCreatedAtFrom().isBefore(filter.getCreatedAtTo())) {
            throw new BlogCommentException(ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private void validateCommentId(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            throw new BlogCommentException(ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST);
        }
    }

    private BlogComment throwTransitionFailure(String commentId) {
        if (blogCommentRepository.findById(commentId).isEmpty()) {
            throw new BlogCommentException(ErrorStatus._COMMENT_NOT_FOUND);
        }
        throw new BlogCommentException(ErrorStatus._COMMENT_STATE_CONFLICT);
    }
}
