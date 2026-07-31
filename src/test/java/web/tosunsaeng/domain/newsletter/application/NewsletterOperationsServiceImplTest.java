package web.tosunsaeng.domain.newsletter.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailTemplateRenderer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NewsletterOperationsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T07:00:00Z");

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private NewsletterCampaignRepository campaignRepository;

    @Mock
    private NewsletterDeliveryRepository deliveryRepository;

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @Mock
    private NewsletterCampaignService campaignService;

    @Mock
    private NewsletterEmailSender emailSender;

    @Mock
    private NewsletterEmailNormalizer emailNormalizer;

    @Mock
    private NewsletterLinkBuilder linkBuilder;

    @Mock
    private NewsletterEmailTemplateRenderer templateRenderer;

    private NewsletterDeliveryProperties properties;
    private NewsletterOperationsServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NewsletterDeliveryProperties();
        service = new NewsletterOperationsServiceImpl(
                blogPostRepository,
                campaignRepository,
                deliveryRepository,
                subscriberRepository,
                campaignService,
                emailSender,
                emailNormalizer,
                linkBuilder,
                templateRenderer,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void testSendRequiresBothSwitchesBeforeAnyRecipientProcessing() {
        properties.setSendingEnabled(false);
        properties.setTestSendingEnabled(true);

        assertThat(service.sendTest("post-id", "allowed@example.test"))
                .isEqualTo(NewsletterOperationsService.TestSendResult.DISABLED);

        properties.setSendingEnabled(true);
        properties.setTestSendingEnabled(false);
        assertThat(service.sendTest("post-id", "allowed@example.test"))
                .isEqualTo(NewsletterOperationsService.TestSendResult.DISABLED);
        verifyNoInteractions(
                emailSender,
                emailNormalizer,
                blogPostRepository,
                deliveryRepository,
                subscriberRepository,
                campaignService);
    }

    @Test
    void allowlistedTestSendHasNoSubscriberTokenHeadersOrPersistence(
            CapturedOutput output) {
        enableTestSending();
        properties.setTestRecipientAllowlist(List.of("allowed@example.test"));
        when(emailNormalizer.normalize(" Allowed@Example.Test "))
                .thenReturn("allowed@example.test");
        when(emailNormalizer.normalize("allowed@example.test"))
                .thenReturn("allowed@example.test");
        when(blogPostRepository.findById("post-id"))
                .thenReturn(Optional.of(post()));
        when(linkBuilder.postUrl("post-slug"))
                .thenReturn("https://www.example.test/blog/post-slug");
        when(templateRenderer.renderTest(
                any(BlogPost.class),
                eq("https://www.example.test/blog/post-slug")))
                .thenReturn(new NewsletterEmailTemplateRenderer.RenderedEmail(
                        "[테스트] [토선생] 제목",
                        "<p>test</p>",
                        "test"));
        when(emailSender.send(any()))
                .thenReturn(new NewsletterEmailSendResult("test-message-id"));

        assertThat(service.sendTest("post-id", " Allowed@Example.Test "))
                .isEqualTo(NewsletterOperationsService.TestSendResult.SENT);

        ArgumentCaptor<NewsletterEmailMessage> captor =
                ArgumentCaptor.forClass(NewsletterEmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().headers()).isEmpty();
        assertThat(captor.getValue().subject()).startsWith("[테스트]");
        assertThat(captor.getValue().htmlBody()).doesNotContain("unsubscribe");
        assertThat(output.getOut())
                .contains("event=newsletter.test_send.accepted")
                .contains("postId=post-id")
                .doesNotContain("allowed@example.test", " Allowed@Example.Test ", "<p>test</p>");
        verifyNoInteractions(deliveryRepository, subscriberRepository, campaignService);
    }

    @Test
    void testRecipientOutsideAllowlistIsRejectedBeforePostOrProvider() {
        enableTestSending();
        properties.setTestRecipientAllowlist(List.of("allowed@example.test"));
        when(emailNormalizer.normalize("other@example.test"))
                .thenReturn("other@example.test");
        when(emailNormalizer.normalize("allowed@example.test"))
                .thenReturn("allowed@example.test");

        assertThatThrownBy(() -> service.sendTest(
                "post-id", "other@example.test"))
                .isInstanceOfSatisfying(
                        NewsletterException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_TEST_RECIPIENT_FORBIDDEN));

        verifyNoInteractions(blogPostRepository, emailSender, deliveryRepository);
    }

    @Test
    void scheduledCancellationDelegatesToAtomicCampaignService() {
        when(campaignService.cancelScheduledCampaign("campaign-id")).thenReturn(true);

        assertThat(service.cancelScheduledCampaign("campaign-id")).isTrue();

        verify(campaignService).cancelScheduledCampaign("campaign-id");
    }

    @Test
    void postCancellationFindsUniqueCampaignAndRejectsStateConflict() {
        when(campaignRepository.findByPostId("post-id"))
                .thenReturn(Optional.of(campaign()));
        when(campaignService.cancelScheduledCampaign("campaign-id"))
                .thenReturn(true, false);

        service.cancelScheduledCampaignByPostId("post-id");

        assertThatThrownBy(() -> service.cancelScheduledCampaignByPostId("post-id"))
                .isInstanceOfSatisfying(
                        NewsletterException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_OPERATION_CONFLICT));
        verify(campaignService, times(2)).cancelScheduledCampaign("campaign-id");
    }

    @ParameterizedTest
    @EnumSource(
            value = NewsletterCampaignStatus.class,
            names = {"SENDING", "SENT", "FAILED", "CANCELED"})
    void nonScheduledCampaignStatesAreCancellationConflicts(
            NewsletterCampaignStatus status) {
        when(campaignRepository.findByPostId("post-id"))
                .thenReturn(Optional.of(campaign(status)));
        when(campaignService.cancelScheduledCampaign("campaign-id"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.cancelScheduledCampaignByPostId("post-id"))
                .isInstanceOfSatisfying(
                        NewsletterException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_OPERATION_CONFLICT));
    }

    @Test
    void missingCampaignIsReportedWithoutExposingPostDetails() {
        when(campaignRepository.findByPostId("missing-post"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelScheduledCampaignByPostId(
                "missing-post"))
                .isInstanceOfSatisfying(
                        NewsletterException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_OPERATION_NOT_FOUND));

        verifyNoInteractions(campaignService);
    }

    @Test
    void activeRetryableFailureRequeuesSameDeliveryAndReopensCampaign() {
        NewsletterDelivery failed = failedDelivery(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                2);
        when(deliveryRepository.findById("delivery-id"))
                .thenReturn(Optional.of(failed));
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(NewsletterSubscriberStatus.ACTIVE)));
        when(deliveryRepository.requeueFailedForManualRetry(
                "delivery-id", 4, NOW)).thenReturn(Optional.of(failed));

        assertThat(service.retryFailedDelivery("delivery-id"))
                .isEqualTo(NewsletterOperationsService.ManualRetryResult.REQUEUED);

        verify(deliveryRepository).requeueFailedForManualRetry(
                "delivery-id", 4, NOW);
        verify(campaignService).reopenFailedCampaign("campaign-id");
        verify(deliveryRepository, never()).save(any());
        verify(deliveryRepository, never()).insert(any(NewsletterDelivery.class));
    }

    @Test
    void unknownResultSentAndSkippedAreNeverManualRetryCandidates() {
        NewsletterDelivery unknown = failedDelivery(
                NewsletterFailureType.PROVIDER_RESULT_UNKNOWN,
                false,
                1);
        when(deliveryRepository.findById("delivery-id"))
                .thenReturn(Optional.of(unknown));

        assertThat(service.retryFailedDelivery("delivery-id"))
                .isEqualTo(NewsletterOperationsService.ManualRetryResult.REJECTED);

        verifyNoInteractions(subscriberRepository, campaignService);
        verify(deliveryRepository, never()).requeueFailedForManualRetry(
                anyString(), any(Integer.class), any());
    }

    @Test
    void sentSkippedNonRetryableAndExhaustedDeliveriesAreRejected() {
        List<NewsletterDelivery> rejected = List.of(
                deliveryWithStatus(NewsletterDeliveryStatus.SENT, true, 1),
                deliveryWithStatus(NewsletterDeliveryStatus.SKIPPED, true, 1),
                deliveryWithStatus(NewsletterDeliveryStatus.FAILED, false, 1),
                deliveryWithStatus(NewsletterDeliveryStatus.FAILED, true, 4));
        for (int index = 0; index < rejected.size(); index++) {
            String deliveryId = "rejected-" + index;
            when(deliveryRepository.findById(deliveryId))
                    .thenReturn(Optional.of(rejected.get(index)));

            assertThat(service.retryFailedDelivery(deliveryId))
                    .isEqualTo(NewsletterOperationsService.ManualRetryResult.REJECTED);
        }

        verifyNoInteractions(subscriberRepository, campaignService, emailSender);
        verify(deliveryRepository, never()).requeueFailedForManualRetry(
                anyString(), anyInt(), any());
    }

    @ParameterizedTest
    @EnumSource(
            value = NewsletterSubscriberStatus.class,
            names = {"UNSUBSCRIBED", "BOUNCED"})
    void inactiveSubscriberConvertsRetryableFailureToSkipped(
            NewsletterSubscriberStatus subscriberStatus) {
        NewsletterDelivery failed = failedDelivery(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                1);
        when(deliveryRepository.findById("delivery-id"))
                .thenReturn(Optional.of(failed));
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(subscriberStatus)));
        when(deliveryRepository.skipFailedInactive("delivery-id", 4, NOW))
                .thenReturn(true);

        assertThat(service.retryFailedDelivery("delivery-id"))
                .isEqualTo(
                        NewsletterOperationsService.ManualRetryResult.SKIPPED_INACTIVE);

        verify(campaignService).reopenFailedCampaign("campaign-id");
        verify(deliveryRepository, never()).requeueFailedForManualRetry(
                anyString(), any(Integer.class), any());
    }

    @Test
    void postRetryProcessesAtMostOneHundredAndReportsMoreWithoutProviderCall() {
        List<String> candidateIds = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> "delivery-" + index)
                .toList();
        when(campaignRepository.findByPostId("post-id"))
                .thenReturn(Optional.of(campaign()));
        when(deliveryRepository.findManualRetryCandidateIds(
                "campaign-id", 4, 101)).thenReturn(candidateIds);
        when(deliveryRepository.findById(anyString()))
                .thenAnswer(invocation -> Optional.of(failedDelivery(
                        invocation.getArgument(0),
                        "subscriber-id",
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        1)));
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(NewsletterSubscriberStatus.ACTIVE)));
        when(deliveryRepository.requeueFailedForManualRetry(
                anyString(), eq(4), eq(NOW)))
                .thenAnswer(invocation -> Optional.of(failedDelivery(
                        invocation.getArgument(0),
                        "subscriber-id",
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        1)));

        NewsletterOperationsService.ManualRetryBatchResult result =
                service.retryFailedDeliveriesByPostId("post-id");

        assertThat(result.retriedCount()).isEqualTo(100);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.hasMore()).isTrue();
        verify(deliveryRepository, times(100)).requeueFailedForManualRetry(
                anyString(), eq(4), eq(NOW));
        verify(deliveryRepository, never()).findById("delivery-101");
        verify(campaignService).reopenFailedCampaign("campaign-id");
        verifyNoInteractions(emailSender);
        verify(deliveryRepository, never()).save(any());
        verify(deliveryRepository, never()).insert(any(NewsletterDelivery.class));
    }

    @Test
    void postRetryCountsInactiveSubscribersAsSkippedWithoutRetryingThem() {
        when(campaignRepository.findByPostId("post-id"))
                .thenReturn(Optional.of(campaign()));
        when(deliveryRepository.findManualRetryCandidateIds(
                "campaign-id", 4, 101))
                .thenReturn(List.of("active-delivery", "inactive-delivery"));
        when(deliveryRepository.findById("active-delivery"))
                .thenReturn(Optional.of(failedDelivery(
                        "active-delivery",
                        "active-subscriber",
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        1)));
        when(deliveryRepository.findById("inactive-delivery"))
                .thenReturn(Optional.of(failedDelivery(
                        "inactive-delivery",
                        "inactive-subscriber",
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        1)));
        when(subscriberRepository.findById("active-subscriber"))
                .thenReturn(Optional.of(subscriber(NewsletterSubscriberStatus.ACTIVE)));
        when(subscriberRepository.findById("inactive-subscriber"))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.UNSUBSCRIBED)));
        when(deliveryRepository.requeueFailedForManualRetry(
                "active-delivery", 4, NOW))
                .thenReturn(Optional.of(failedDelivery(
                        "active-delivery",
                        "active-subscriber",
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        1)));
        when(deliveryRepository.skipFailedInactive(
                "inactive-delivery", 4, NOW)).thenReturn(true);

        NewsletterOperationsService.ManualRetryBatchResult result =
                service.retryFailedDeliveriesByPostId("post-id");

        assertThat(result.retriedCount()).isOne();
        assertThat(result.skippedCount()).isOne();
        assertThat(result.hasMore()).isFalse();
        verify(deliveryRepository, never()).requeueFailedForManualRetry(
                eq("inactive-delivery"), anyInt(), any());
        verify(campaignService).reopenFailedCampaign("campaign-id");
        verifyNoInteractions(emailSender);
    }

    @Test
    void postRetryWithNoEligibleOrWinningDeliveryIsConflict() {
        when(campaignRepository.findByPostId("post-id"))
                .thenReturn(Optional.of(campaign()));
        when(deliveryRepository.findManualRetryCandidateIds(
                "campaign-id", 4, 101)).thenReturn(List.of());

        assertThatThrownBy(() -> service.retryFailedDeliveriesByPostId("post-id"))
                .isInstanceOfSatisfying(
                        NewsletterException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(
                                ErrorStatus._NEWSLETTER_OPERATION_CONFLICT));

        verifyNoInteractions(subscriberRepository, campaignService, emailSender);
    }

    private void enableTestSending() {
        properties.setSendingEnabled(true);
        properties.setTestSendingEnabled(true);
    }

    private BlogPost post() {
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

    private NewsletterDelivery failedDelivery(
            NewsletterFailureType failureType,
            boolean retryable,
            int attemptCount) {
        return failedDelivery(
                "delivery-id",
                "subscriber-id",
                failureType,
                retryable,
                attemptCount);
    }

    private NewsletterDelivery failedDelivery(
            String deliveryId,
            String subscriberId,
            NewsletterFailureType failureType,
            boolean retryable,
            int attemptCount) {
        return NewsletterDelivery.builder()
                .id(deliveryId)
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId(subscriberId)
                .status(NewsletterDeliveryStatus.FAILED)
                .attemptCount(attemptCount)
                .lastErrorCode(failureType)
                .retryable(retryable)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterCampaign campaign() {
        return campaign(NewsletterCampaignStatus.FAILED);
    }

    private NewsletterCampaign campaign(NewsletterCampaignStatus status) {
        return NewsletterCampaign.builder()
                .id("campaign-id")
                .postId("post-id")
                .status(status)
                .scheduledAt(NOW.minusSeconds(600))
                .createdAt(NOW.minusSeconds(600))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterDelivery deliveryWithStatus(
            NewsletterDeliveryStatus status,
            boolean retryable,
            int attemptCount) {
        return NewsletterDelivery.builder()
                .id("delivery-id")
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId("subscriber-id")
                .status(status)
                .attemptCount(attemptCount)
                .lastErrorCode(NewsletterFailureType.TRANSIENT_PROVIDER)
                .retryable(retryable)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterSubscriber subscriber(NewsletterSubscriberStatus status) {
        return NewsletterSubscriber.builder()
                .id("subscriber-id")
                .email("user@example.test")
                .status(status)
                .tokenVersion(1)
                .consentAt(NOW.minusSeconds(100))
                .subscribedAt(NOW.minusSeconds(100))
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }
}
