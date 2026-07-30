package web.tosunsaeng.domain.blog.comment.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentRule {

    COMMENT_MIN_LENGTH(1, "COMMENT_MIN_LENGTH", "댓글은 최소 2자 이상 입력해 주세요."),
    COMMENT_MAX_LENGTH(2, "COMMENT_MAX_LENGTH", "댓글은 최대 500자까지 입력할 수 있습니다."),
    COMMENT_TRIM(3, "COMMENT_TRIM", "댓글 앞뒤 공백을 제거합니다."),
    COMMENT_PLAIN_TEXT_ONLY(4, "COMMENT_PLAIN_TEXT_ONLY", "댓글은 JSON 문자열 형식의 일반 텍스트만 입력해 주세요."),
    COMMENT_HTML_NOT_ALLOWED(5, "COMMENT_HTML_NOT_ALLOWED", "HTML은 댓글에 사용할 수 없습니다."),
    COMMENT_MARKDOWN_NOT_ALLOWED(6, "COMMENT_MARKDOWN_NOT_ALLOWED", "Markdown 문법은 댓글에 사용할 수 없습니다."),
    COMMENT_URL_NOT_ALLOWED(7, "COMMENT_URL_NOT_ALLOWED", "URL과 이메일 주소는 댓글에 입력할 수 없습니다."),
    COMMENT_EMPTY(8, "COMMENT_EMPTY", "댓글 내용을 입력해 주세요."),
    COMMENT_SPAM_PATTERN(9, "COMMENT_SPAM_PATTERN", "과도하게 반복되는 내용을 줄여 주세요."),
    COMMENT_POST_NOT_PUBLIC(10, "COMMENT_POST_NOT_PUBLIC", "댓글을 작성할 수 있는 공개 게시글을 찾을 수 없습니다.");

    private final int ruleNumber;
    private final String ruleCode;
    private final String defaultMessage;
}
