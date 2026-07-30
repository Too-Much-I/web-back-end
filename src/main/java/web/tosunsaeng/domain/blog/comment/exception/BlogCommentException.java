package web.tosunsaeng.domain.blog.comment.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class BlogCommentException extends GeneralException {

    public BlogCommentException(BaseErrorCode code) {
        super(code);
    }
}
