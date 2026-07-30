package web.tosunsaeng.domain.blog.comment.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Getter
@Document(collection = "anonymous_visitors")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnonymousVisitor {

    @Id
    private String id;
    private String tokenHash;
    private String nickname;
    private String avatarSeed;
    private String avatarImageKey;
    private Instant createdAt;
    private Instant lastSeenAt;

    @Builder
    public AnonymousVisitor(
            String id,
            String tokenHash,
            String nickname,
            String avatarSeed,
            String avatarImageKey,
            Instant createdAt,
            Instant lastSeenAt) {
        this.id = id;
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.nickname = Objects.requireNonNull(nickname);
        this.avatarSeed = Objects.requireNonNull(avatarSeed);
        this.avatarImageKey = Objects.requireNonNull(avatarImageKey);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt);
    }

    public void touch(Instant now) {
        this.lastSeenAt = Objects.requireNonNull(now);
    }

    public void updateProfile(
            String nickname,
            String avatarSeed,
            String avatarImageKey,
            Instant now) {
        this.nickname = Objects.requireNonNull(nickname);
        this.avatarSeed = Objects.requireNonNull(avatarSeed);
        this.avatarImageKey = Objects.requireNonNull(avatarImageKey);
        this.lastSeenAt = Objects.requireNonNull(now);
    }
}
