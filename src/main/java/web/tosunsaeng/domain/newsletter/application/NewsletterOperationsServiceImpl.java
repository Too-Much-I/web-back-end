package web.tosunsaeng.domain.newsletter.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterDeliveryStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailNormalizer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterEmailTemplateRenderer;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterOperationsServiceImpl implements NewsletterOperationsService {

    static final int MANUAL_RETRY_BATCH_SIZE = 100;

    private final BlogPostRepository blogPostRepository;
    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterDeliveryRepository deliveryRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterCampaignService campaignService;
    private final NewsletterEmailSender emailSender;
    private final NewsletterEmailNormalizer emailNormalizer;
    private final NewsletterLinkBuilder linkBuilder;
    private final NewsletterEmailTemplateRenderer templateRenderer;
    private final NewsletterDeliveryProperties properties;
    private final Clock clock;

    @Override
    public TestSendResult sendTest(String postId, String recipientEmail) {
        if (!properties.isSendingEnabled() || !properties.isTestSendingEnabled()) {
            return TestSendResult.DISABLED;
        }
        String normalizedRecipient = emailNormalizer.normalize(recipientEmail);
        Set<String> allowed = properties.getTestRecipientAllowlist().stream()
                .map(emailNormalizer::normalize)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowed.contains(normalizedRecipient)) {
            throw new NewsletterException(
                    ErrorStatus._NEWSLETTER_TEST_RECIPIENT_FORBIDDEN);
        }
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new NewsletterException(
                        ErrorStatus._NEWSLETTER_OPERATION_NOT_FOUND));
        NewsletterEmailTemplateRenderer.RenderedEmail rendered = templateRenderer.renderTest(
                post,
                linkBuilder.postUrl(post.getSlug()));
        emailSender.send(new NewsletterEmailMessage(
                normalizedRecipient,
                rendered.subject(),
                rendered.htmlBody(),
                rendered.plainTextBody(),
                Map.of()));
        log.info(
                "event=newsletter.test_send.accepted postId={} provider={}",
                post.getId(),
                properties.getEmailProvider());
        return TestSendResult.SENT;
    }

    @Override
    public boolean cancelScheduledCampaign(String campaignId) {
        return campaignService.cancelScheduledCampaign(campaignId);
    }

    @Override
    public void cancelScheduledCampaignByPostId(String postId) {
        NewsletterCampaign campaign = findCampaign(postId);
        if (!cancelScheduledCampaign(campaign.getId())) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_OPERATION_CONFLICT);
        }
    }

    @Override
    public ManualRetryResult retryFailedDelivery(String deliveryId) {
        ManualRetryOutcome outcome = retryFailedDeliveryWithoutCampaignReopen(
                deliveryId);
        if (outcome.result() != ManualRetryResult.REJECTED) {
            campaignService.reopenFailedCampaign(outcome.campaignId());
        }
        return outcome.result();
    }

    @Override
    public ManualRetryBatchResult retryFailedDeliveriesByPostId(String postId) {
        NewsletterCampaign campaign = findCampaign(postId);
        var candidateIds = deliveryRepository.findManualRetryCandidateIds(
                campaign.getId(),
                NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS,
                MANUAL_RETRY_BATCH_SIZE + 1);
        boolean hasMore = candidateIds.size() > MANUAL_RETRY_BATCH_SIZE;
        int retriedCount = 0;
        int skippedCount = 0;
        for (String deliveryId : candidateIds.stream()
                .limit(MANUAL_RETRY_BATCH_SIZE)
                .toList()) {
            ManualRetryOutcome result = retryFailedDeliveryWithoutCampaignReopen(
                    deliveryId);
            if (result.result() == ManualRetryResult.REQUEUED) {
                retriedCount++;
            } else if (result.result() == ManualRetryResult.SKIPPED_INACTIVE) {
                skippedCount++;
            }
        }
        if (retriedCount + skippedCount == 0) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_OPERATION_CONFLICT);
        }
        campaignService.reopenFailedCampaign(campaign.getId());
        return new ManualRetryBatchResult(retriedCount, skippedCount, hasMore);
    }

    private ManualRetryOutcome retryFailedDeliveryWithoutCampaignReopen(
            String deliveryId) {
        NewsletterDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElse(null);
        if (!isManualRetryCandidate(delivery)) {
            return new ManualRetryOutcome(ManualRetryResult.REJECTED, null);
        }
        NewsletterSubscriber subscriber = subscriberRepository
                .findById(delivery.getSubscriberId())
                .orElse(null);
        if (subscriber == null || subscriber.getStatus() != NewsletterSubscriberStatus.ACTIVE) {
            if (deliveryRepository.skipFailedInactive(
                    deliveryId,
                    NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS,
                    clock.instant())) {
                return new ManualRetryOutcome(
                        ManualRetryResult.SKIPPED_INACTIVE,
                        delivery.getCampaignId());
            }
            return new ManualRetryOutcome(ManualRetryResult.REJECTED, null);
        }
        return deliveryRepository.requeueFailedForManualRetry(
                        deliveryId,
                        NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS,
                        clock.instant())
                .map(requeued -> {
                    return new ManualRetryOutcome(
                            ManualRetryResult.REQUEUED,
                            requeued.getCampaignId());
                })
                .orElseGet(() -> new ManualRetryOutcome(
                        ManualRetryResult.REJECTED,
                        null));
    }

    private NewsletterCampaign findCampaign(String postId) {
        return campaignRepository.findByPostId(postId)
                .orElseThrow(() -> new NewsletterException(
                        ErrorStatus._NEWSLETTER_OPERATION_NOT_FOUND));
    }

    private boolean isManualRetryCandidate(NewsletterDelivery delivery) {
        return delivery != null
                && delivery.getStatus() == NewsletterDeliveryStatus.FAILED
                && delivery.isRetryable()
                && delivery.getAttemptCount() < NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS
                && delivery.getLastErrorCode()
                != NewsletterFailureType.PROVIDER_RESULT_UNKNOWN;
    }

    private record ManualRetryOutcome(
            ManualRetryResult result,
            String campaignId) {
    }
}
