package web.tosunsaeng.domain.exams.exception;

import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.exception.GeneralException;

public class ExamsException extends GeneralException {

    public ExamsException(BaseErrorCode code) { super(code); }
}