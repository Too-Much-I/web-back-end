package web.tosunsaeng.domain.blog.comment.exception;

import lombok.Getter;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.Objects;

@Getter
public class CommentRateLimitException extends BlogCommentException {

    private final long retryAfterSeconds;
    private final CommentLimitScope limitScope;

    public CommentRateLimitException(
            long retryAfterSeconds,
            CommentLimitScope limitScope) {
        super(ErrorStatus._COMMENT_RATE_LIMITED);
        if (retryAfterSeconds < 1) {
            throw new IllegalArgumentException("retryAfterSeconds는 1 이상이어야 합니다.");
        }
        this.retryAfterSeconds = retryAfterSeconds;
        this.limitScope = Objects.requireNonNull(limitScope);
    }
}
