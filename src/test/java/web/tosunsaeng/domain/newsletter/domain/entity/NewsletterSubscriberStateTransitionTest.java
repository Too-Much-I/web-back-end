package web.tosunsaeng.domain.newsletter.domain.entity;

import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterSubscriberStateTransitionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-31T00:00:00Z");
    private static final Instant CHANGED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void newSubscriberIsImmediatelyActiveWithoutPendingState() {
        NewsletterSubscriber subscriber = NewsletterSubscriber.newActive(
                "user@example.com", CREATED_AT);

        assertThat(subscriber.getStatus()).isEqualTo(NewsletterSubscriberStatus.ACTIVE);
        assertThat(subscriber.getTokenVersion()).isEqualTo(1);
        assertThat(subscriber.getConsentAt()).isEqualTo(CREATED_AT);
        assertThat(subscriber.getSubscribedAt()).isEqualTo(CREATED_AT);
        assertThat(subscriber.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(subscriber.getUpdatedAt()).isEqualTo(CREATED_AT);
        assertThat(subscriber.getUnsubscribedAt()).isNull();
        assertThat(NewsletterSubscriberStatus.values())
                .containsExactly(
                        NewsletterSubscriberStatus.ACTIVE,
                        NewsletterSubscriberStatus.UNSUBSCRIBED,
                        NewsletterSubscriberStatus.BOUNCED);
    }

    @Test
    void unsubscribedSubscriberReactivatesAndIncrementsVersion() {
        NewsletterSubscriber subscriber = subscriber(
                NewsletterSubscriberStatus.UNSUBSCRIBED,
                3,
                CREATED_AT);

        subscriber.reactivate(CHANGED_AT);

        assertThat(subscriber.getStatus()).isEqualTo(NewsletterSubscriberStatus.ACTIVE);
        assertThat(subscriber.getTokenVersion()).isEqualTo(4);
        assertThat(subscriber.getConsentAt()).isEqualTo(CHANGED_AT);
        assertThat(subscriber.getSubscribedAt()).isEqualTo(CHANGED_AT);
        assertThat(subscriber.getUnsubscribedAt()).isNull();
        assertThat(subscriber.getUpdatedAt()).isEqualTo(CHANGED_AT);
        assertThat(subscriber.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void activeAndBouncedCanUnsubscribeWithoutChangingVersion() {
        NewsletterSubscriber active = subscriber(
                NewsletterSubscriberStatus.ACTIVE, 2, null);
        NewsletterSubscriber bounced = subscriber(
                NewsletterSubscriberStatus.BOUNCED, 4, null);

        assertThat(active.unsubscribe(CHANGED_AT)).isTrue();
        assertThat(bounced.unsubscribe(CHANGED_AT)).isTrue();

        assertThat(active.getStatus()).isEqualTo(NewsletterSubscriberStatus.UNSUBSCRIBED);
        assertThat(active.getTokenVersion()).isEqualTo(2);
        assertThat(bounced.getStatus()).isEqualTo(NewsletterSubscriberStatus.UNSUBSCRIBED);
        assertThat(bounced.getTokenVersion()).isEqualTo(4);
        assertThat(active.getUnsubscribedAt()).isEqualTo(CHANGED_AT);
        assertThat(bounced.getUnsubscribedAt()).isEqualTo(CHANGED_AT);
    }

    @Test
    void repeatedUnsubscribeIsIdempotentAndKeepsTimestamp() {
        NewsletterSubscriber subscriber = subscriber(
                NewsletterSubscriberStatus.UNSUBSCRIBED,
                2,
                CREATED_AT);

        assertThat(subscriber.unsubscribe(CHANGED_AT)).isFalse();
        assertThat(subscriber.getUnsubscribedAt()).isEqualTo(CREATED_AT);
        assertThat(subscriber.getUpdatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void activeOrBouncedCannotUseResubscribeTransition() {
        assertThatThrownBy(() -> subscriber(
                NewsletterSubscriberStatus.ACTIVE, 1, null).reactivate(CHANGED_AT))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscriber(
                NewsletterSubscriberStatus.BOUNCED, 1, null).reactivate(CHANGED_AT))
                .isInstanceOf(IllegalStateException.class);
    }

    private NewsletterSubscriber subscriber(
            NewsletterSubscriberStatus status,
            long tokenVersion,
            Instant unsubscribedAt) {
        return NewsletterSubscriber.builder()
                .id("subscriber-id")
                .email("user@example.com")
                .status(status)
                .tokenVersion(tokenVersion)
                .consentAt(CREATED_AT)
                .subscribedAt(CREATED_AT)
                .unsubscribedAt(unsubscribedAt)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .build();
    }
}
