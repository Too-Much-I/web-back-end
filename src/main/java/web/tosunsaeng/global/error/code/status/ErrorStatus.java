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

    // Recipe
    _RECIPE_SEARCH_FAIL(HttpStatus.BAD_REQUEST, "RECIPE_4000", "레시피 추천에 실패했습니다."),
    _RECIPE_NOT_FOUND(HttpStatus.BAD_REQUEST, "RECIPE_4001", "존재하지 않는 레시피입니다."),
    _SCRAP_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "RECIPE_4001", "이미 찜한 레시피입니다."),
    _SCRAP_NOT_FOUND(HttpStatus.NOT_FOUND, "RECIPE_4004", "찜한 내역이 존재하지 않습니다.");

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
