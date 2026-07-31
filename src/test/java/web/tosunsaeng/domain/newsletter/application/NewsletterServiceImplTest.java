package web.tosunsaeng.domain.newsletter.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.newsletter.converter.NewsletterConverter;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterUnsubscribeTokenManager;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");
    private static final String RAW_EMAIL = " User@Example.COM ";
    private static final String NORMALIZED_EMAIL = "user@example.com";
    private static final String CLIENT_IP = "203.0.113.10";

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @Mock
    private NewsletterRateLimitService rateLimitService;

    @Mock
    private NewsletterEmailNormalizer emailNormalizer;

    @Mock
    private NewsletterUnsubscribeTokenManager tokenManager;

    private NewsletterServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NewsletterServiceImpl(
                subscriberRepository,
                rateLimitService,
                emailNormalizer,
                tokenManager,
                new NewsletterConverter(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rateLimitRunsBeforeEmailValidationForEveryDeserializedRequest() {
        when(emailNormalizer.normalize(RAW_EMAIL))
                .thenThrow(new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_INVALID));

        assertThatThrownBy(() -> service.subscribe(subscribe(true), CLIENT_IP))
                .isInstanceOf(NewsletterException.class);

        InOrder order = inOrder(rateLimitService, emailNormalizer);
        order.verify(rateLimitService).check(CLIENT_IP);
        order.verify(emailNormalizer).normalize(RAW_EMAIL);
        verifyNoInteractions(subscriberRepository);
    }

    @Test
    void consentFalseIsRejectedAfterRateLimitWithoutMongoAccess() {
        when(emailNormalizer.normalize(RAW_EMAIL)).thenReturn(NORMALIZED_EMAIL);

        assertThatThrownBy(() -> service.subscribe(subscribe(false), CLIENT_IP))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo(ErrorStatus._NEWSLETTER_CONSENT_REQUIRED));

        verify(rateLimitService).check(CLIENT_IP);
        verifyNoInteractions(subscriberRepository);
    }

    @Test
    void nullDeserializedRequestStillCountsThenFailsEmailValidation() {
        when(emailNormalizer.normalize(null))
                .thenThrow(new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_REQUIRED));

        assertThatThrownBy(() -> service.subscribe(null, CLIENT_IP))
                .isInstanceOf(NewsletterException.class);

        verify(rateLimitService).check(CLIENT_IP);
        verify(emailNormalizer).normalize(null);
    }

    @Test
    void newSubscriberIsInsertedActiveWithAllApprovedTimestamps() {
        prepareValidSubscribe();
        when(subscriberRepository.reactivateByEmail(NORMALIZED_EMAIL, NOW))
                .thenReturn(Optional.empty());
        when(subscriberRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.empty());
        when(subscriberRepository.insert(any(NewsletterSubscriber.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NewsletterResponseDTO.StatusResult result =
                service.subscribe(subscribe(true), CLIENT_IP);

        ArgumentCaptor<NewsletterSubscriber> captor =
                ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).insert(captor.capture());
        NewsletterSubscriber inserted = captor.getValue();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(inserted.getEmail()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(inserted.getStatus()).isEqualTo(NewsletterSubscriberStatus.ACTIVE);
        assertThat(inserted.getTokenVersion()).isEqualTo(1);
        assertThat(inserted.getConsentAt()).isEqualTo(NOW);
        assertThat(inserted.getSubscribedAt()).isEqualTo(NOW);
        assertThat(inserted.getCreatedAt()).isEqualTo(NOW);
        assertThat(inserted.getUpdatedAt()).isEqualTo(NOW);
        assertThat(inserted.getUnsubscribedAt()).isNull();
    }

    @Test
    void existingActiveSubscriptionIsIdempotentWithoutTimestampMutation() {
        prepareValidSubscribe();
        NewsletterSubscriber active = subscriber(
                NewsletterSubscriberStatus.ACTIVE, 7, NOW.minusSeconds(100));
        when(subscriberRepository.reactivateByEmail(NORMALIZED_EMAIL, NOW))
                .thenReturn(Optional.empty());
        when(subscriberRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(active));
        Instant originalUpdatedAt = active.getUpdatedAt();

        NewsletterResponseDTO.StatusResult result =
                service.subscribe(subscribe(true), CLIENT_IP);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(active.getUpdatedAt()).isEqualTo(originalUpdatedAt);
        assertThat(active.getTokenVersion()).isEqualTo(7);
        verify(subscriberRepository, never()).insert(any(NewsletterSubscriber.class));
    }

    @Test
    void unsubscribedSubscriptionReactivatesAtomically() {
        prepareValidSubscribe();
        NewsletterSubscriber reactivated = subscriber(
                NewsletterSubscriberStatus.ACTIVE, 3, NOW);
        when(subscriberRepository.reactivateByEmail(NORMALIZED_EMAIL, NOW))
                .thenReturn(Optional.of(reactivated));

        assertThat(service.subscribe(subscribe(true), CLIENT_IP).getStatus())
                .isEqualTo("ACTIVE");

        verify(subscriberRepository, never()).findByEmail(any());
        verify(subscriberRepository, never()).insert(any(NewsletterSubscriber.class));
    }

    @Test
    void bouncedSubscriptionReturnsGenericConflictWithoutMutation() {
        prepareValidSubscribe();
        when(subscriberRepository.reactivateByEmail(NORMALIZED_EMAIL, NOW))
                .thenReturn(Optional.empty());
        when(subscriberRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.BOUNCED, 2, NOW)));

        assertThatThrownBy(() -> service.subscribe(subscribe(true), CLIENT_IP))
                .isInstanceOfSatisfying(NewsletterException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo(ErrorStatus._NEWSLETTER_SUBSCRIPTION_UNAVAILABLE);
                    assertThat(exception.getCode().getMessage())
                            .doesNotContain("BOUNCED")
                            .doesNotContain(NORMALIZED_EMAIL);
                });
        verify(subscriberRepository, never()).insert(any(NewsletterSubscriber.class));
    }

    @Test
    void duplicateInsertRaceRequeriesAndConvergesToActiveSuccess() {
        prepareValidSubscribe();
        NewsletterSubscriber active = subscriber(
                NewsletterSubscriberStatus.ACTIVE, 1, NOW);
        when(subscriberRepository.reactivateByEmail(NORMALIZED_EMAIL, NOW))
                .thenReturn(Optional.empty(), Optional.empty());
        when(subscriberRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.empty(), Optional.of(active));
        when(subscriberRepository.insert(any(NewsletterSubscriber.class)))
                .thenThrow(new DuplicateKeyException("duplicate email driver detail"));

        assertThat(service.subscribe(subscribe(true), CLIENT_IP).getStatus())
                .isEqualTo("ACTIVE");

        verify(subscriberRepository, times(2))
                .findByEmail(NORMALIZED_EMAIL);
        verify(subscriberRepository).insert(any(NewsletterSubscriber.class));
    }

    @Test
    void activeOrBouncedUnsubscribeUsesVerifiedIdAndVersion() {
        when(tokenManager.verify("signed-token"))
                .thenReturn(claims(4));
        when(subscriberRepository.unsubscribeByIdAndVersion(
                "subscriber-id", 4, NOW))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.UNSUBSCRIBED, 4, NOW)));

        NewsletterResponseDTO.StatusResult result = service.unsubscribe(
                new NewsletterRequestDTO.UnsubscribeRequest("signed-token"));

        assertThat(result.getStatus()).isEqualTo("UNSUBSCRIBED");
        verify(subscriberRepository).unsubscribeByIdAndVersion(
                "subscriber-id", 4, NOW);
        verify(subscriberRepository, never()).findById(any());
    }

    @Test
    void repeatedUnsubscribeWithSameVersionIsIdempotentWithoutUpdate() {
        when(tokenManager.verify("signed-token")).thenReturn(claims(4));
        when(subscriberRepository.unsubscribeByIdAndVersion(
                "subscriber-id", 4, NOW)).thenReturn(Optional.empty());
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.UNSUBSCRIBED, 4, NOW.minusSeconds(60))));

        assertThat(service.unsubscribe(
                new NewsletterRequestDTO.UnsubscribeRequest("signed-token"))
                .getStatus()).isEqualTo("UNSUBSCRIBED");
    }

    @Test
    void oldVersionOrMissingSubscriberUsesSameInvalidTokenError() {
        when(tokenManager.verify("signed-token")).thenReturn(claims(3));
        when(subscriberRepository.unsubscribeByIdAndVersion(
                "subscriber-id", 3, NOW)).thenReturn(Optional.empty());
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.ACTIVE, 4, NOW)));

        assertThatThrownBy(() -> service.unsubscribe(
                new NewsletterRequestDTO.UnsubscribeRequest("signed-token")))
                .isInstanceOfSatisfying(NewsletterException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID));
    }

    private void prepareValidSubscribe() {
        when(emailNormalizer.normalize(RAW_EMAIL)).thenReturn(NORMALIZED_EMAIL);
    }

    private NewsletterRequestDTO.SubscribeRequest subscribe(boolean consent) {
        return new NewsletterRequestDTO.SubscribeRequest(RAW_EMAIL, consent);
    }

    private NewsletterUnsubscribeTokenManager.TokenClaims claims(long version) {
        return new NewsletterUnsubscribeTokenManager.TokenClaims(
                1,
                "active-v1",
                "subscriber-id",
                version,
                NOW.minusSeconds(60));
    }

    private NewsletterSubscriber subscriber(
            NewsletterSubscriberStatus status,
            long version,
            Instant updatedAt) {
        return NewsletterSubscriber.builder()
                .id("subscriber-id")
                .email(NORMALIZED_EMAIL)
                .status(status)
                .tokenVersion(version)
                .consentAt(NOW.minusSeconds(100))
                .subscribedAt(NOW.minusSeconds(100))
                .unsubscribedAt(status == NewsletterSubscriberStatus.UNSUBSCRIBED
                        ? updatedAt : null)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(updatedAt)
                .build();
    }
}
