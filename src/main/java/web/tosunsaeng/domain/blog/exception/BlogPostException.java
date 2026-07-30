package web.tosunsaeng.domain.blog.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class BlogPostException extends GeneralException {

    public BlogPostException(BaseErrorCode code) {
        super(code);
    }
}
