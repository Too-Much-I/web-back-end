package web.tosunsaeng.domain.comment.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;

import java.time.Instant;
import java.util.Optional;

public interface BlogCommentQueryRepository {

    Page<BlogComment> findVisibleCommentsByPostId(String postId, Pageable pageable);

    Page<BlogComment> findCommentsForModeration(
            CommentStatus status,
            String postId,
            Instant createdAtFrom,
            Instant createdAtTo,
            Pageable pageable);

    Optional<BlogComment> hideComment(
            String commentId,
            HiddenReason reason,
            Instant now);

    Optional<BlogComment> restoreComment(String commentId, Instant now);
}
