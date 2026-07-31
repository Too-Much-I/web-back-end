package web.tosunsaeng.domain.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;

import java.time.Instant;
import java.util.List;

public class BlogCommentModerationDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentFilter {
        private CommentStatus status;
        private String postId;
        private String slug;
        private Instant createdAtFrom;
        private Instant createdAtTo;
        private int page;
        private int size;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModeratedCommentResult {
        private String commentId;
        private String postId;
        private String postSlug;
        private String nickname;
        private String avatarImageUrl;
        private String content;
        private CommentStatus status;
        private Instant createdAt;
        private Instant hiddenAt;
        private HiddenReason hiddenReason;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationTransitionResult {
        private String id;
        private CommentStatus status;
        private HiddenReason hiddenReason;
        private Instant hiddenAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModeratedCommentPageResult {
        private List<ModeratedCommentResult> comments;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }
}
