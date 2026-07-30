package web.tosunsaeng.domain.blog.comment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

public class BlogCommentResponseDTO {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentSummary {
        private String id;
        private String nickname;
        private String avatarSeed;
        private String avatarImageUrl;
        private String content;
        private Instant createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentPageResult {
        private List<CommentSummary> comments;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
        private boolean hasNext;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedCommentResult {
        private String id;
        private String nickname;
        private String avatarSeed;
        private String avatarImageUrl;
        private String content;
        private Instant createdAt;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnonymousProfileResult {
        private String nickname;
        private String avatarSeed;
        private String avatarImageUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViolationResult {
        private int ruleNumber;
        private String ruleCode;
        private String message;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationFailureResult {
        private List<ViolationResult> violations;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RateLimitFailureResult {
        private long retryAfterSeconds;
        private String limitScope;
    }
}
