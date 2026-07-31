package web.tosunsaeng.domain.newsletter.domain.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;

import java.time.Instant;
import java.util.Objects;

@Getter
@Document(collection = "newsletter_subscribers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsletterSubscriber {

    @Id
    private String id;
    private String email;
    private NewsletterSubscriberStatus status;
    private long tokenVersion;
    private Instant consentAt;
    private Instant subscribedAt;
    private Instant unsubscribedAt;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder
    public NewsletterSubscriber(
            String id,
            String email,
            NewsletterSubscriberStatus status,
            long tokenVersion,
            Instant consentAt,
            Instant subscribedAt,
            Instant unsubscribedAt,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email);
        this.status = Objects.requireNonNull(status);
        if (tokenVersion < 1) {
            throw new IllegalArgumentException("tokenVersion은 1 이상이어야 합니다.");
        }
        this.tokenVersion = tokenVersion;
        this.consentAt = Objects.requireNonNull(consentAt);
        this.subscribedAt = Objects.requireNonNull(subscribedAt);
        this.unsubscribedAt = unsubscribedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static NewsletterSubscriber newActive(String normalizedEmail, Instant now) {
        Instant requiredNow = Objects.requireNonNull(now);
        return NewsletterSubscriber.builder()
                .email(Objects.requireNonNull(normalizedEmail))
                .status(NewsletterSubscriberStatus.ACTIVE)
                .tokenVersion(1)
                .consentAt(requiredNow)
                .subscribedAt(requiredNow)
                .unsubscribedAt(null)
                .createdAt(requiredNow)
                .updatedAt(requiredNow)
                .build();
    }

    public void reactivate(Instant now) {
        if (status != NewsletterSubscriberStatus.UNSUBSCRIBED) {
            throw new IllegalStateException("현재 상태에서는 재구독할 수 없습니다.");
        }
        Instant requiredNow = Objects.requireNonNull(now);
        status = NewsletterSubscriberStatus.ACTIVE;
        tokenVersion++;
        consentAt = requiredNow;
        subscribedAt = requiredNow;
        unsubscribedAt = null;
        updatedAt = requiredNow;
    }

    public boolean unsubscribe(Instant now) {
        if (status == NewsletterSubscriberStatus.UNSUBSCRIBED) {
            return false;
        }
        if (status != NewsletterSubscriberStatus.ACTIVE
                && status != NewsletterSubscriberStatus.BOUNCED) {
            throw new IllegalStateException("현재 상태에서는 구독을 해지할 수 없습니다.");
        }
        Instant requiredNow = Objects.requireNonNull(now);
        status = NewsletterSubscriberStatus.UNSUBSCRIBED;
        unsubscribedAt = requiredNow;
        updatedAt = requiredNow;
        return true;
    }
}
