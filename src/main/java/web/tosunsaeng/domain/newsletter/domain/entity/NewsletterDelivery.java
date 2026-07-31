package web.tosunsaeng.domain.newsletter.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;

import java.time.Instant;
import java.util.Objects;

@Getter
@Document(collection = "newsletter_deliveries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsletterDelivery {

    @Id
    private String id;
    private String campaignId;
    private String postId;
    private String subscriberId;
    private NewsletterDeliveryStatus status;
    private int attemptCount;
    private Instant nextRetryAt;
    private String claimToken;
    private Instant claimExpiresAt;
    private Instant providerCallStartedAt;
    private String providerMessageId;
    private Instant sentAt;
    private Instant failedAt;
    private Instant skippedAt;
    private NewsletterFailureType lastErrorCode;
    private boolean retryable;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder
    public NewsletterDelivery(
            String id,
            String campaignId,
            String postId,
            String subscriberId,
            NewsletterDeliveryStatus status,
            int attemptCount,
            Instant nextRetryAt,
            String claimToken,
            Instant claimExpiresAt,
            Instant providerCallStartedAt,
            String providerMessageId,
            Instant sentAt,
            Instant failedAt,
            Instant skippedAt,
            NewsletterFailureType lastErrorCode,
            boolean retryable,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.campaignId = Objects.requireNonNull(campaignId);
        this.postId = Objects.requireNonNull(postId);
        this.subscriberId = Objects.requireNonNull(subscriberId);
        this.status = Objects.requireNonNull(status);
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount는 0 이상이어야 합니다.");
        }
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.claimToken = claimToken;
        this.claimExpiresAt = claimExpiresAt;
        this.providerCallStartedAt = providerCallStartedAt;
        this.providerMessageId = providerMessageId;
        this.sentAt = sentAt;
        this.failedAt = failedAt;
        this.skippedAt = skippedAt;
        this.lastErrorCode = lastErrorCode;
        this.retryable = retryable;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static NewsletterDelivery pending(
            String campaignId,
            String postId,
            String subscriberId,
            Instant now) {
        return NewsletterDelivery.builder()
                .campaignId(campaignId)
                .postId(postId)
                .subscriberId(subscriberId)
                .status(NewsletterDeliveryStatus.PENDING)
                .attemptCount(0)
                .retryable(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
