package web.tosunsaeng.domain.newsletter.application;

import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterDelivery;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
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

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
public class NewsletterDeliveryServiceImpl implements NewsletterDeliveryService {

    private final NewsletterDeliveryRepository deliveryRepository;
    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final BlogPostRepository blogPostRepository;
    private final NewsletterEmailSender emailSender;
    private final NewsletterEmailNormalizer emailNormalizer;
    private final NewsletterUnsubscribeTokenManager tokenManager;
    private final NewsletterLinkBuilder linkBuilder;
    private final NewsletterEmailTemplateRenderer templateRenderer;
    private final NewsletterRetryPolicy retryPolicy;
    private final NewsletterDeliveryProperties properties;
    private final Clock clock;
    private final NewsletterClaimTokenGenerator claimTokenGenerator;

    public NewsletterDeliveryServiceImpl(
            NewsletterDeliveryRepository deliveryRepository,
            NewsletterCampaignRepository campaignRepository,
            NewsletterSubscriberRepository subscriberRepository,
            BlogPostRepository blogPostRepository,
            NewsletterEmailSender emailSender,
            NewsletterEmailNormalizer emailNormalizer,
            NewsletterUnsubscribeTokenManager tokenManager,
            NewsletterLinkBuilder linkBuilder,
            NewsletterEmailTemplateRenderer templateRenderer,
            NewsletterRetryPolicy retryPolicy,
            NewsletterDeliveryProperties properties,
            Clock clock,
            NewsletterClaimTokenGenerator claimTokenGenerator) {
        this.deliveryRepository = deliveryRepository;
        this.campaignRepository = campaignRepository;
        this.subscriberRepository = subscriberRepository;
        this.blogPostRepository = blogPostRepository;
        this.emailSender = emailSender;
        this.emailNormalizer = emailNormalizer;
        this.tokenManager = tokenManager;
        this.linkBuilder = linkBuilder;
        this.templateRenderer = templateRenderer;
        this.retryPolicy = retryPolicy;
        this.properties = properties;
        this.clock = clock;
        this.claimTokenGenerator = claimTokenGenerator;
    }

    @Override
    public void processNextPendingDelivery() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant now = clock.instant();
        deliveryRepository.claimNextPending(
                        now,
                        claimExpiresAt(now),
                        claimTokenGenerator.create())
                .ifPresent(this::sendClaimedDelivery);
    }

    @Override
    public void processNextRetryDelivery() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant now = clock.instant();
        deliveryRepository.claimNextRetry(
                        now,
                        claimExpiresAt(now),
                        claimTokenGenerator.create(),
                        NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS)
                .ifPresent(this::sendClaimedDelivery);
    }

    @Override
    public void recoverNextStaleDelivery() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant now = clock.instant();
        deliveryRepository.recoverNextStaleBeforeProvider(now);
        deliveryRepository.failNextStaleAfterProvider(now);
    }

    private void sendClaimedDelivery(NewsletterDelivery delivery) {
        if (!properties.isSendingEnabled()) {
            return;
        }
        if (!campaignRepository.existsByIdAndStatus(
                delivery.getCampaignId(),
                NewsletterCampaignStatus.SENDING)) {
            deliveryRepository.releaseClaimToPending(
                    delivery.getId(),
                    delivery.getClaimToken(),
                    clock.instant());
            return;
        }
        Optional<NewsletterSubscriber> subscriberCandidate =
                subscriberRepository.findById(delivery.getSubscriberId());
        if (subscriberCandidate.isEmpty()
                || subscriberCandidate.orElseThrow().getStatus()
                != NewsletterSubscriberStatus.ACTIVE) {
            deliveryRepository.markSkipped(
                    delivery.getId(),
                    delivery.getClaimToken(),
                    clock.instant());
            return;
        }
        Optional<BlogPost> postCandidate = blogPostRepository.findById(delivery.getPostId());
        if (postCandidate.isEmpty() || !isStillEligible(postCandidate.orElseThrow())) {
            deliveryRepository.markSkipped(
                    delivery.getId(),
                    delivery.getClaimToken(),
                    clock.instant());
            return;
        }

        NewsletterSubscriber subscriber = subscriberCandidate.orElseThrow();
        BlogPost post = postCandidate.orElseThrow();
        String recipient;
        try {
            recipient = emailNormalizer.normalize(subscriber.getEmail());
        } catch (NewsletterException exception) {
            failBeforeProvider(delivery, NewsletterFailureType.INVALID_EMAIL);
            return;
        } catch (RuntimeException exception) {
            failBeforeProvider(delivery, NewsletterFailureType.APPLICATION_ERROR);
            return;
        }

        NewsletterEmailMessage message;
        try {
            String token = tokenManager.createToken(
                    subscriber.getId(),
                    subscriber.getTokenVersion());
            String postUrl = linkBuilder.postUrl(post.getSlug());
            NewsletterEmailTemplateRenderer.RenderedEmail rendered = templateRenderer.render(
                    post,
                    postUrl,
                    linkBuilder.manualUnsubscribeUrl(token));
            message = new NewsletterEmailMessage(
                    recipient,
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.plainTextBody(),
                    linkBuilder.oneClickHeaders(token));
        } catch (RuntimeException exception) {
            failBeforeProvider(delivery, NewsletterFailureType.APPLICATION_ERROR);
            return;
        }

        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant providerStartedAt = clock.instant();
        Optional<NewsletterDelivery> started = deliveryRepository.markProviderCallStarted(
                delivery.getId(),
                delivery.getClaimToken(),
                providerStartedAt,
                claimExpiresAt(providerStartedAt));
        if (started.isEmpty()) {
            return;
        }

        try {
            NewsletterEmailSendResult result = emailSender.send(message);
            deliveryRepository.markSent(
                    delivery.getId(),
                    delivery.getClaimToken(),
                    result.providerMessageId(),
                    clock.instant());
        } catch (NewsletterEmailSendException exception) {
            handleProviderFailure(started.orElseThrow(), exception);
        } catch (RuntimeException exception) {
            deliveryRepository.markFailed(
                    delivery.getId(),
                    delivery.getClaimToken(),
                    NewsletterFailureType.APPLICATION_ERROR,
                    false,
                    null,
                    clock.instant());
        }
    }

    private boolean isStillEligible(BlogPost post) {
        return post.getStatus() == BlogPostStatus.PUBLISHED
                && post.getPublishedAt() != null
                && !post.getPublishedAt().isAfter(clock.instant())
                && post.isNewsletterEnabled();
    }

    private void failBeforeProvider(
            NewsletterDelivery delivery,
            NewsletterFailureType failureType) {
        deliveryRepository.markFailed(
                delivery.getId(),
                delivery.getClaimToken(),
                failureType,
                false,
                null,
                clock.instant());
    }

    private void handleProviderFailure(
            NewsletterDelivery started,
            NewsletterEmailSendException exception) {
        Instant failedAt = clock.instant();
        if (!exception.isProviderResultKnown()) {
            deliveryRepository.markFailed(
                    started.getId(),
                    started.getClaimToken(),
                    NewsletterFailureType.PROVIDER_RESULT_UNKNOWN,
                    false,
                    null,
                    failedAt);
            return;
        }
        Optional<Instant> nextRetryAt = exception.isRetryable()
                ? retryPolicy.nextRetryAt(started.getAttemptCount(), failedAt)
                : Optional.empty();
        deliveryRepository.markFailed(
                started.getId(),
                started.getClaimToken(),
                exception.getFailureType(),
                nextRetryAt.isPresent(),
                nextRetryAt.orElse(null),
                failedAt);
    }

    private Instant claimExpiresAt(Instant now) {
        return now.plusSeconds(properties.getClaimTtlSeconds());
    }

}
