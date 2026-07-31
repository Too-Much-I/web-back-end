package web.tosunsaeng.domain.newsletter.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterClaimTokenGenerator;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailTemplateRenderer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterUnsubscribeTokenManager;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendException;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterDeliveryServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T06:00:00Z");

    @Mock
    private NewsletterDeliveryRepository deliveryRepository;

    @Mock
    private NewsletterCampaignRepository campaignRepository;

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private NewsletterEmailSender emailSender;

    @Mock
    private NewsletterEmailNormalizer emailNormalizer;

    @Mock
    private NewsletterUnsubscribeTokenManager tokenManager;

    @Mock
    private NewsletterLinkBuilder linkBuilder;

    @Mock
    private NewsletterEmailTemplateRenderer templateRenderer;

    private NewsletterDeliveryProperties properties;
    private NewsletterDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NewsletterDeliveryProperties();
        properties.setSendingEnabled(true);
        service = new NewsletterDeliveryServiceImpl(
                deliveryRepository,
                campaignRepository,
                subscriberRepository,
                blogPostRepository,
                emailSender,
                emailNormalizer,
                tokenManager,
                linkBuilder,
                templateRenderer,
                new NewsletterRetryPolicy(),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NewsletterClaimTokenGenerator());
    }

    @Test
    void pendingDeliveryUsesFencedProviderBoundaryAndStoresMessageId() {
        NewsletterDelivery claimed = preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost());
        NewsletterDelivery started = startedDelivery(1);
        prepareMessage(claimed, started);
        when(emailSender.send(any(NewsletterEmailMessage.class)))
                .thenReturn(new NewsletterEmailSendResult("ses-message-id"));

        service.processNextPendingDelivery();

        ArgumentCaptor<NewsletterEmailMessage> messageCaptor =
                ArgumentCaptor.forClass(NewsletterEmailMessage.class);
        verify(emailSender).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().recipient()).isEqualTo("user@example.test");
        assertThat(messageCaptor.getValue().headers())
                .containsEntry(NewsletterLinkBuilder.LIST_UNSUBSCRIBE, "<one-click>")
                .containsEntry(
                        NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST,
                        NewsletterLinkBuilder.ONE_CLICK_FORM_VALUE);
        verify(deliveryRepository).markProviderCallStarted(
                "delivery-id", "claim-token", NOW, NOW.plusSeconds(300));
        verify(deliveryRepository).markSent(
                "delivery-id", "claim-token", "ses-message-id", NOW);
    }

    @ParameterizedTest
    @EnumSource(value = NewsletterSubscriberStatus.class, names = {
            "UNSUBSCRIBED", "BOUNCED"
    })
    void inactiveSubscriberIsSkippedBeforeTokenOrProvider(
            NewsletterSubscriberStatus status) {
        NewsletterDelivery claimed = claimedDelivery(0);
        when(deliveryRepository.claimNextPending(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenReturn(Optional.of(claimed));
        when(campaignRepository.existsByIdAndStatus(
                "campaign-id", NewsletterCampaignStatus.SENDING)).thenReturn(true);
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(status)));

        service.processNextPendingDelivery();

        verify(deliveryRepository).markSkipped("delivery-id", "claim-token", NOW);
        verifyNoInteractions(tokenManager, emailSender, blogPostRepository);
        verify(deliveryRepository, never()).markProviderCallStarted(
                anyString(), anyString(), any(), any());
    }

    @Test
    void campaignNotSendingReleasesClaimWithoutCallingProvider() {
        NewsletterDelivery claimed = claimedDelivery(0);
        when(deliveryRepository.claimNextPending(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenReturn(Optional.of(claimed));
        when(campaignRepository.existsByIdAndStatus(
                "campaign-id", NewsletterCampaignStatus.SENDING)).thenReturn(false);

        service.processNextPendingDelivery();

        verify(deliveryRepository).releaseClaimToPending(
                "delivery-id", "claim-token", NOW);
        verifyNoInteractions(subscriberRepository, blogPostRepository, emailSender);
    }

    @Test
    void postNoLongerEligibleIsSkippedBeforeProvider() {
        NewsletterDelivery claimed = preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                BlogPost.builder()
                        .id("post-id")
                        .slug("post-slug")
                        .title("제목")
                        .summary("요약")
                        .status(BlogPostStatus.ARCHIVED)
                        .publishedAt(NOW.minusSeconds(60))
                        .newsletterEnabled(true)
                        .createdAt(NOW)
                        .updatedAt(NOW)
                        .build());

        service.processNextPendingDelivery();

        verify(deliveryRepository).markSkipped(
                claimed.getId(), claimed.getClaimToken(), NOW);
        verifyNoInteractions(tokenManager, emailSender);
    }

    @Test
    void invalidRecipientFailsBeforeAttemptIncrementOrProviderCall() {
        NewsletterDelivery claimed = preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost());
        when(emailNormalizer.normalize("user@example.test"))
                .thenThrow(new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_INVALID));

        service.processNextPendingDelivery();

        verify(deliveryRepository).markFailed(
                "delivery-id",
                "claim-token",
                NewsletterFailureType.INVALID_EMAIL,
                false,
                null,
                NOW);
        verify(deliveryRepository, never()).markProviderCallStarted(
                anyString(), anyString(), any(), any());
        verifyNoInteractions(emailSender);
    }

    @Test
    void tokenOrTemplateContractFailureIsApplicationErrorNotInvalidEmail() {
        preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost());
        when(emailNormalizer.normalize("user@example.test"))
                .thenReturn("user@example.test");
        when(tokenManager.createToken("subscriber-id", 3))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID));

        service.processNextPendingDelivery();

        verify(deliveryRepository).markFailed(
                "delivery-id",
                "claim-token",
                NewsletterFailureType.APPLICATION_ERROR,
                false,
                null,
                NOW);
        verify(deliveryRepository, never()).markProviderCallStarted(
                anyString(), anyString(), any(), any());
        verifyNoInteractions(emailSender);
    }

    @Test
    void transientFirstAttemptSchedulesRetryFiveMinutesLater() {
        NewsletterDelivery claimed = preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost());
        NewsletterDelivery started = startedDelivery(1);
        prepareMessage(claimed, started);
        when(emailSender.send(any())).thenThrow(new NewsletterEmailSendException(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                true));

        service.processNextPendingDelivery();

        verify(deliveryRepository).markFailed(
                "delivery-id",
                "claim-token",
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                NOW.plusSeconds(5 * 60),
                NOW);
    }

    @Test
    void fourthAttemptAndPermanentFailureHaveNoAutomaticRetry() {
        NewsletterDelivery claimed = prepareRetryClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost(),
                3);
        NewsletterDelivery started = startedDelivery(4);
        prepareMessage(claimed, started);
        when(emailSender.send(any())).thenThrow(new NewsletterEmailSendException(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                true));

        service.processNextRetryDelivery();

        verify(deliveryRepository).markFailed(
                "delivery-id",
                "claim-token",
                NewsletterFailureType.TRANSIENT_PROVIDER,
                false,
                null,
                NOW);
    }

    @Test
    void providerOutcomeUnknownIsFinalAndNeverScheduled() {
        NewsletterDelivery claimed = preparePendingClaim(
                NewsletterSubscriberStatus.ACTIVE,
                eligiblePost());
        NewsletterDelivery started = startedDelivery(1);
        prepareMessage(claimed, started);
        when(emailSender.send(any())).thenThrow(new NewsletterEmailSendException(
                NewsletterFailureType.PROVIDER_RESULT_UNKNOWN,
                false,
                false));

        service.processNextPendingDelivery();

        verify(deliveryRepository).markFailed(
                "delivery-id",
                "claim-token",
                NewsletterFailureType.PROVIDER_RESULT_UNKNOWN,
                false,
                null,
                NOW);
    }

    @Test
    void retryRechecksCurrentSubscriberAndSkipsAfterUnsubscribe() {
        NewsletterDelivery claimed = claimedDelivery(1);
        when(deliveryRepository.claimNextRetry(
                eq(NOW),
                eq(NOW.plusSeconds(300)),
                anyString(),
                eq(NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS)))
                .thenReturn(Optional.of(claimed));
        when(campaignRepository.existsByIdAndStatus(
                "campaign-id", NewsletterCampaignStatus.SENDING)).thenReturn(true);
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.UNSUBSCRIBED)));

        service.processNextRetryDelivery();

        verify(deliveryRepository).markSkipped("delivery-id", "claim-token", NOW);
        verifyNoInteractions(emailSender, tokenManager);
    }

    @Test
    void staleRecoveryRunsBothSafeAndUnknownBranchesOnlyWhenEnabled() {
        service.recoverNextStaleDelivery();

        verify(deliveryRepository).recoverNextStaleBeforeProvider(NOW);
        verify(deliveryRepository).failNextStaleAfterProvider(NOW);

        org.mockito.Mockito.clearInvocations(deliveryRepository);
        properties.setSendingEnabled(false);
        service.recoverNextStaleDelivery();
        verifyNoInteractions(deliveryRepository);
    }

    @Test
    void killSwitchFalseDoesNotClaimCallProviderOrMutateExistingSendingState() {
        properties.setSendingEnabled(false);

        service.processNextPendingDelivery();
        service.processNextRetryDelivery();
        service.recoverNextStaleDelivery();

        verifyNoInteractions(
                deliveryRepository,
                campaignRepository,
                subscriberRepository,
                blogPostRepository,
                emailSender);
    }

    @Test
    void switchTurningOffAfterClaimDoesNotForceAnyStateTransition() {
        when(deliveryRepository.claimNextPending(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenAnswer(invocation -> {
                    properties.setSendingEnabled(false);
                    return Optional.of(claimedDelivery(0));
                });

        service.processNextPendingDelivery();

        verify(deliveryRepository).claimNextPending(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString());
        verify(deliveryRepository, never()).releaseClaimToPending(anyString(), anyString(), any());
        verify(deliveryRepository, never()).markSkipped(anyString(), anyString(), any());
        verifyNoInteractions(emailSender, campaignRepository, subscriberRepository);
    }

    private NewsletterDelivery preparePendingClaim(
            NewsletterSubscriberStatus status,
            BlogPost post) {
        NewsletterDelivery claimed = claimedDelivery(0);
        when(deliveryRepository.claimNextPending(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenReturn(Optional.of(claimed));
        prepareCampaignSubscriberAndPost(status, post);
        return claimed;
    }

    private NewsletterDelivery prepareRetryClaim(
            NewsletterSubscriberStatus status,
            BlogPost post,
            int attempts) {
        NewsletterDelivery claimed = claimedDelivery(attempts);
        when(deliveryRepository.claimNextRetry(
                eq(NOW),
                eq(NOW.plusSeconds(300)),
                anyString(),
                eq(NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS)))
                .thenReturn(Optional.of(claimed));
        prepareCampaignSubscriberAndPost(status, post);
        return claimed;
    }

    private void prepareCampaignSubscriberAndPost(
            NewsletterSubscriberStatus status,
            BlogPost post) {
        when(campaignRepository.existsByIdAndStatus(
                "campaign-id", NewsletterCampaignStatus.SENDING)).thenReturn(true);
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(status)));
        when(blogPostRepository.findById("post-id")).thenReturn(Optional.of(post));
    }

    private void prepareMessage(
            NewsletterDelivery claimed,
            NewsletterDelivery started) {
        when(emailNormalizer.normalize("user@example.test"))
                .thenReturn("user@example.test");
        when(tokenManager.createToken("subscriber-id", 3)).thenReturn("signed-token");
        when(linkBuilder.postUrl("post-slug")).thenReturn("https://public/post");
        when(linkBuilder.manualUnsubscribeUrl("signed-token"))
                .thenReturn("https://public/unsubscribe");
        when(linkBuilder.oneClickHeaders("signed-token")).thenReturn(Map.of(
                NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
                "<one-click>",
                NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST,
                NewsletterLinkBuilder.ONE_CLICK_FORM_VALUE));
        when(templateRenderer.render(
                any(BlogPost.class),
                eq("https://public/post"),
                eq("https://public/unsubscribe")))
                .thenReturn(new NewsletterEmailTemplateRenderer.RenderedEmail(
                        "[토선생] 제목",
                        "<p>html</p>",
                        "plain"));
        when(deliveryRepository.markProviderCallStarted(
                claimed.getId(), claimed.getClaimToken(), NOW, NOW.plusSeconds(300)))
                .thenReturn(Optional.of(started));
    }

    private NewsletterDelivery claimedDelivery(int attempts) {
        return NewsletterDelivery.builder()
                .id("delivery-id")
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId("subscriber-id")
                .status(NewsletterDeliveryStatus.SENDING)
                .attemptCount(attempts)
                .claimToken("claim-token")
                .claimExpiresAt(NOW.plusSeconds(300))
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterDelivery startedDelivery(int attempts) {
        return NewsletterDelivery.builder()
                .id("delivery-id")
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId("subscriber-id")
                .status(NewsletterDeliveryStatus.SENDING)
                .attemptCount(attempts)
                .claimToken("claim-token")
                .claimExpiresAt(NOW.plusSeconds(300))
                .providerCallStartedAt(NOW)
                .createdAt(NOW.minusSeconds(60))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterSubscriber subscriber(NewsletterSubscriberStatus status) {
        return NewsletterSubscriber.builder()
                .id("subscriber-id")
                .email("user@example.test")
                .status(status)
                .tokenVersion(3)
                .consentAt(NOW.minusSeconds(100))
                .subscribedAt(NOW.minusSeconds(100))
                .unsubscribedAt(status == NewsletterSubscriberStatus.UNSUBSCRIBED
                        ? NOW.minusSeconds(10) : null)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }

    private BlogPost eligiblePost() {
        return BlogPost.builder()
                .id("post-id")
                .slug("post-slug")
                .title("제목")
                .summary("요약")
                .status(BlogPostStatus.PUBLISHED)
                .publishedAt(NOW.minusSeconds(60))
                .newsletterEnabled(true)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }
}
