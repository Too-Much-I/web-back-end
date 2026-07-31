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
    RECIPE_DELETE_SCRAP(HttpStatus.OK, "RECIPE_204", "성공적으로 레피시 찜을 취소했습니다."),

    // Blog
    BLOG_POST_LIST(HttpStatus.OK, "BLOG_200", "게시글 목록을 조회했습니다."),
    BLOG_POST_DETAIL(HttpStatus.OK, "BLOG_201", "게시글 상세를 조회했습니다."),
    BLOG_POST_SEARCH(HttpStatus.OK, "BLOG_202", "게시글을 검색했습니다."),

    // Blog Comment
    BLOG_COMMENT_LIST(HttpStatus.OK, "COMMENT_200", "댓글 목록을 조회했습니다."),
    BLOG_COMMENT_CREATED(HttpStatus.CREATED, "COMMENT_201", "댓글을 작성했습니다."),
    ANONYMOUS_PROFILE_REGENERATED(HttpStatus.OK, "COMMENT_202", "익명 프로필을 재생성했습니다."),
    BLOG_COMMENT_REQUEST_ACCEPTED(HttpStatus.ACCEPTED, "COMMENT_203", "요청이 접수되었습니다."),

    // Newsletter
    NEWSLETTER_SUBSCRIBED(
            HttpStatus.OK,
            "NEWSLETTER_200",
            "뉴스레터 구독 요청이 처리되었습니다."),
    NEWSLETTER_UNSUBSCRIBED(
            HttpStatus.OK,
            "NEWSLETTER_201",
            "뉴스레터 구독이 해지되었습니다.");

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
