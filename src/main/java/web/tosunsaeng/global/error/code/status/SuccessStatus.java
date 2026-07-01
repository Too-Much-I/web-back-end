package web.tosunsaeng.global.error.code.status;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

    // Common
    OK(HttpStatus.OK, "COMMON_200", "성공입니다."),

    // Member
    MEMBER_INFO(HttpStatus.OK, "MEMBER_200", "성공적으로 유저 정보를 조회했습니다."),
    MEMBER_PREFERENCE(HttpStatus.ACCEPTED, "MEMBER_201", "성공적으로 유저 건강 정보 및 기호를 수정했습니다."),
    MEMBER_SCRAPS(HttpStatus.OK, "MEMBER_202", "성공적으로 유저의 찜한 레시피 목록을 조회했습니다."),

    // Recipe
    RECIPE(HttpStatus.OK, "RECIPE_200", "성공적으로 레시피를 조회했습니다."),
    RECIPE_INFO(HttpStatus.OK, "RECIPE_201", "성공적으로 레시피의 상세 정보를 조회했습니다."),
    RECIPE_FIND(HttpStatus.OK, "RECIPE_202", "성공적으로 레시피를 검색했습니다."),
    RECIPE_SCRAP(HttpStatus.OK, "RECIPE_203", "성공적으로 레피시를 찜했습니다."),
    RECIPE_DELETE_SCRAP(HttpStatus.OK, "RECIPE_204", "성공적으로 레피시 찜을 취소했습니다.");

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
    public ReasonDTO getReasonHttpStatus() {
        return ReasonDTO.builder()
                .message(message)
                .code(code)
                .isSuccess(true)
                .httpStatus(httpStatus)
                .build();
    }
}