package web.tosunsaeng.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.error.code.status.ErrorReasonDTO;


@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException {

    private BaseErrorCode code;

    public ErrorReasonDTO getErrorReasonHttpStatus() {
        return this.code.getReasonHttpStatus();
    }
}
