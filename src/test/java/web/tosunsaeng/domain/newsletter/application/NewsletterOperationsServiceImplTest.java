package web.tosunsaeng.domain.newsletter.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailTemplateRenderer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendResult;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class NewsletterOperationsServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T07:00:00Z");

    @Mock
    private BlogPostRepository blogPostRepository;

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
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("other@example.test");

        verifyNoInteractions(blogPostRepository, emailSender, deliveryRepository);
    }

    @Test
    void scheduledCancellationDelegatesToAtomicCampaignService() {
        when(campaignService.cancelScheduledCampaign("campaign-id")).thenReturn(true);

        assertThat(service.cancelScheduledCampaign("campaign-id")).isTrue();

        verify(campaignService).cancelScheduledCampaign("campaign-id");
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
    void inactiveSubscriberConvertsRetryableFailureToSkipped() {
        NewsletterDelivery failed = failedDelivery(
                NewsletterFailureType.TRANSIENT_PROVIDER,
                true,
                1);
        when(deliveryRepository.findById("delivery-id"))
                .thenReturn(Optional.of(failed));
        when(subscriberRepository.findById("subscriber-id"))
                .thenReturn(Optional.of(subscriber(
                        NewsletterSubscriberStatus.UNSUBSCRIBED)));
        when(deliveryRepository.skipFailedInactive("delivery-id", NOW))
                .thenReturn(true);

        assertThat(service.retryFailedDelivery("delivery-id"))
                .isEqualTo(
                        NewsletterOperationsService.ManualRetryResult.SKIPPED_INACTIVE);

        verify(campaignService).reopenFailedCampaign("campaign-id");
        verify(deliveryRepository, never()).requeueFailedForManualRetry(
                anyString(), any(Integer.class), any());
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
        return NewsletterDelivery.builder()
                .id("delivery-id")
                .campaignId("campaign-id")
                .postId("post-id")
                .subscriberId("subscriber-id")
                .status(NewsletterDeliveryStatus.FAILED)
                .attemptCount(attemptCount)
                .lastErrorCode(failureType)
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
