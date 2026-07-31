package web.tosunsaeng.domain.newsletter.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.time.Instant;
import java.util.Objects;

@Getter
@Document(collection = "newsletter_campaigns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsletterCampaign {

    @Id
    private String id;
    private String postId;
    private NewsletterCampaignStatus status;
    private Instant scheduledAt;
    private Instant claimedAt;
    private String claimToken;
    private Instant claimExpiresAt;
    private Instant completedAt;
    private Instant canceledAt;
    private Long totalRecipients;
    private long sentCount;
    private long failedCount;
    private long skippedCount;
    private NewsletterFailureType lastErrorCode;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder
    public NewsletterCampaign(
            String id,
            String postId,
            NewsletterCampaignStatus status,
            Instant scheduledAt,
            Instant claimedAt,
            String claimToken,
            Instant claimExpiresAt,
            Instant completedAt,
            Instant canceledAt,
            Long totalRecipients,
            long sentCount,
            long failedCount,
            long skippedCount,
            NewsletterFailureType lastErrorCode,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.postId = Objects.requireNonNull(postId);
        this.status = Objects.requireNonNull(status);
        this.scheduledAt = Objects.requireNonNull(scheduledAt);
        this.claimedAt = claimedAt;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.completedAt = completedAt;
        this.canceledAt = canceledAt;
        this.totalRecipients = totalRecipients;
        this.sentCount = requireNonNegative(sentCount, "sentCount");
        this.failedCount = requireNonNegative(failedCount, "failedCount");
        this.skippedCount = requireNonNegative(skippedCount, "skippedCount");
        this.lastErrorCode = lastErrorCode;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static NewsletterCampaign scheduled(
            String postId,
            Instant scheduledAt,
            Instant now) {
        return NewsletterCampaign.builder()
                .postId(postId)
                .status(NewsletterCampaignStatus.SCHEDULED)
                .scheduledAt(scheduledAt)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private long requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "은 0 이상이어야 합니다.");
        }
        return value;
    }
}
