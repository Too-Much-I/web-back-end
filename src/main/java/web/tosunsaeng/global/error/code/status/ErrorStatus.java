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

    // Exams
    _EXAM_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4004", "해당 모의고사 세션을 찾을 수 없습니다."),
    _EXAM_PAPER_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4005", "해당 문제지를 찾을 수 없습니다."),
    _AI_SERVER_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXAM_4001", "AI 채점 서버와 통신할 수 없습니다. 잠시 후 다시 시도해주세요."),
    _QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "EXAM_4002", "해당 문제를 찾을 수 없습니다."),
    _EXAM_ALREADY_COMPLETED(HttpStatus.ALREADY_REPORTED, "EXAM_4003", "이미 채점이 완료된 시험 세션입니다."),
    _AI_SERVER_PROCESSING_NOW(HttpStatus.BAD_REQUEST, "EXAM_4006", "현재 채점이 진행 중입니다. 잠시 후 다시 시도해 주세요."),

    // Blog
    _BLOG_SEARCH_QUERY_TOO_SHORT(HttpStatus.BAD_REQUEST, "BLOG_4001", "검색어는 앞뒤 공백 제거 후 2자 이상이어야 합니다."),
    _BLOG_SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "BLOG_4002", "검색어는 앞뒤 공백 제거 후 50자 이하여야 합니다."),
    _BLOG_PAGE_NEGATIVE(HttpStatus.BAD_REQUEST, "BLOG_4003", "page는 0 이상이어야 합니다."),
    _BLOG_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "BLOG_4004", "게시글을 찾을 수 없습니다."),
    _BLOG_SIZE_TOO_SMALL(HttpStatus.BAD_REQUEST, "BLOG_4005", "size는 1 이상이어야 합니다."),
    _BLOG_SIZE_TOO_LARGE(HttpStatus.BAD_REQUEST, "BLOG_4006", "size는 100 이하여야 합니다."),

    // Blog Comment
    _COMMENT_VALIDATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "COMMENT_4001",
            "댓글 작성 규칙을 확인해 주세요."),
    _COMMENT_MODERATION_INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "COMMENT_4002",
            "댓글 운영 요청이 올바르지 않습니다."),
    _COMMENT_HIDDEN_REASON_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "COMMENT_4003",
            "댓글 숨김 사유가 필요합니다."),
    _COMMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "COMMENT_4004",
            "댓글을 찾을 수 없습니다."),
    _COMMENT_STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "COMMENT_4091",
            "현재 댓글 상태에서는 요청한 작업을 수행할 수 없습니다."),
    _COMMENT_RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "COMMENT_4290",
            "댓글 작성 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    // Newsletter
    _NEWSLETTER_EMAIL_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "NEWSLETTER_4001",
            "이메일을 입력해 주세요."),
    _NEWSLETTER_EMAIL_INVALID(
            HttpStatus.BAD_REQUEST,
            "NEWSLETTER_4002",
            "올바른 이메일 형식을 입력해 주세요."),
    _NEWSLETTER_EMAIL_TOO_LONG(
            HttpStatus.BAD_REQUEST,
            "NEWSLETTER_4003",
            "이메일은 254자 이하여야 합니다."),
    _NEWSLETTER_CONSENT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "NEWSLETTER_4004",
            "뉴스레터 수신 동의가 필요합니다."),
    _NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID(
            HttpStatus.BAD_REQUEST,
            "NEWSLETTER_4005",
            "구독 해지 요청이 올바르지 않습니다."),
    _NEWSLETTER_SUBSCRIPTION_UNAVAILABLE(
            HttpStatus.CONFLICT,
            "NEWSLETTER_4091",
            "뉴스레터 구독 요청을 처리할 수 없습니다."),
    _NEWSLETTER_SUBSCRIBE_RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "NEWSLETTER_4290",
            "뉴스레터 구독 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");

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
