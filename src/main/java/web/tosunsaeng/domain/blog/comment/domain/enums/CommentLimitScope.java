package web.tosunsaeng.domain.blog.comment.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommentLimitScope {
    DUPLICATE(1),
    VISITOR_DAILY(2),
    IP_DAILY(3),
    VISITOR_MEDIUM(4),
    IP_MEDIUM(5),
    VISITOR_SHORT(6);

    private final int tiePriority;
}
