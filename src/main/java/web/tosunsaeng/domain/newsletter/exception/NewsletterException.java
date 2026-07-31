package web.tosunsaeng.domain.newsletter.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class NewsletterException extends GeneralException {

    public NewsletterException(BaseErrorCode code) {
        super(code);
    }
}
