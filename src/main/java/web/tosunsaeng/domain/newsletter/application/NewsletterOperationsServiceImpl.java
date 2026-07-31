package web.tosunsaeng.domain.newsletter.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
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
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSender;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterOperationsServiceImpl implements NewsletterOperationsService {

    private final BlogPostRepository blogPostRepository;
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
            throw new IllegalArgumentException("허용되지 않은 newsletter test recipient입니다.");
        }
        BlogPost post = blogPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("test newsletter post가 없습니다."));
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
    public ManualRetryResult retryFailedDelivery(String deliveryId) {
        NewsletterDelivery delivery = deliveryRepository.findById(deliveryId)
                .orElse(null);
        if (!isManualRetryCandidate(delivery)) {
            return ManualRetryResult.REJECTED;
        }
        NewsletterSubscriber subscriber = subscriberRepository
                .findById(delivery.getSubscriberId())
                .orElse(null);
        if (subscriber == null || subscriber.getStatus() != NewsletterSubscriberStatus.ACTIVE) {
            if (deliveryRepository.skipFailedInactive(deliveryId, clock.instant())) {
                campaignService.reopenFailedCampaign(delivery.getCampaignId());
                return ManualRetryResult.SKIPPED_INACTIVE;
            }
            return ManualRetryResult.REJECTED;
        }
        return deliveryRepository.requeueFailedForManualRetry(
                        deliveryId,
                        NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS,
                        clock.instant())
                .map(requeued -> {
                    campaignService.reopenFailedCampaign(requeued.getCampaignId());
                    return ManualRetryResult.REQUEUED;
                })
                .orElse(ManualRetryResult.REJECTED);
    }

    private boolean isManualRetryCandidate(NewsletterDelivery delivery) {
        return delivery != null
                && delivery.getStatus() == NewsletterDeliveryStatus.FAILED
                && delivery.isRetryable()
                && delivery.getAttemptCount() < NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS
                && delivery.getLastErrorCode()
                != NewsletterFailureType.PROVIDER_RESULT_UNKNOWN;
    }
}
