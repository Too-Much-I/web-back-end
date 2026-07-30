package web.tosunsaeng.domain.blog.comment.exception;

import lombok.Getter;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentValidator;
import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

import java.util.List;

@Getter
public class CommentValidationException extends GeneralException {

    private final List<CommentValidator.Violation> violations;

    public CommentValidationException(
            BaseErrorCode code,
            List<CommentValidator.Violation> violations) {
        super(code);
        this.violations = List.copyOf(violations);
    }
}
