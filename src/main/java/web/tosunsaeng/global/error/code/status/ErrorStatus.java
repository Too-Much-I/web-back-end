package web.tosunsaeng.global.error.code.status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode {

    // 기본 에러
    _INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 관리자에게 문의 바랍니다."),
    _BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    _UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    _FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),

    // Member
    _MEMBER_NOT_FOUND(HttpStatus.FORBIDDEN, "MEMBER_4000", "없는 유저 입니다."),

    // Exams (새로 추가!)
    _EXAM_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4004", "해당 모의고사 세션을 찾을 수 없습니다."),
    _EXAM_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4005", "해당 문제지를 찾을 수 없습니다."),
    _AI_SERVER_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXAM_4001", "AI 채점 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    _QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4002", "해당 문제를 찾을 수 없습니다."),
    _EXAM_ALREADY_COMPLETED(HttpStatus.ALREADY_REPORTED, "EXAM_4003", "이미 채점이 완료된 시험 세션입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorReasonDTO getReasonHttpStatus() {
        return ErrorReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }
}
