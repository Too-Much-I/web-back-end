package web.tosunsaeng.domain.blog.comment.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.blog.comment.domain.enums.HiddenReason;

import java.time.Instant;

@Getter
@Document(collection = "blog_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogComment {

    @Id
    private String id;
    private String postId;
    private String anonymousVisitorId;
    private String nickname;
    private String avatarSeed;
    private String avatarImageKey;
    private String content;
    private CommentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant hiddenAt;
    private HiddenReason hiddenReason;

    @Builder
    public BlogComment(
            String id,
            String postId,
            String anonymousVisitorId,
            String nickname,
            String avatarSeed,
            String avatarImageKey,
            String content,
            CommentStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant hiddenAt,
            HiddenReason hiddenReason) {
        this.id = id;
        this.postId = postId;
        this.anonymousVisitorId = anonymousVisitorId;
        this.nickname = nickname;
        this.avatarSeed = avatarSeed;
        this.avatarImageKey = avatarImageKey;
        this.content = content;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.hiddenAt = hiddenAt;
        this.hiddenReason = hiddenReason;
    }
}
