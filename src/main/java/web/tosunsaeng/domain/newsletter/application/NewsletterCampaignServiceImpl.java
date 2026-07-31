package web.tosunsaeng.domain.newsletter.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterClaimTokenGenerator;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryQueryRepository.DeliveryCounts;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class NewsletterCampaignServiceImpl implements NewsletterCampaignService {

    private final BlogPostRepository blogPostRepository;
    private final NewsletterCampaignRepository campaignRepository;
    private final NewsletterDeliveryRepository deliveryRepository;
    private final NewsletterSubscriberRepository subscriberRepository;
    private final NewsletterDeliveryProperties properties;
    private final Clock clock;
    private final NewsletterClaimTokenGenerator claimTokenGenerator;

    public NewsletterCampaignServiceImpl(
            BlogPostRepository blogPostRepository,
            NewsletterCampaignRepository campaignRepository,
            NewsletterDeliveryRepository deliveryRepository,
            NewsletterSubscriberRepository subscriberRepository,
            NewsletterDeliveryProperties properties,
            Clock clock,
            NewsletterClaimTokenGenerator claimTokenGenerator) {
        this.blogPostRepository = blogPostRepository;
        this.campaignRepository = campaignRepository;
        this.deliveryRepository = deliveryRepository;
        this.subscriberRepository = subscriberRepository;
        this.properties = properties;
        this.clock = clock;
        this.claimTokenGenerator = claimTokenGenerator;
    }

    @Override
    public void reconcileCampaigns() {
        String lastSeenId = null;
        while (true) {
            List<BlogPost> posts = blogPostRepository.findNewsletterEligiblePostsAfter(
                    lastSeenId,
                    properties.getBatchSize());
            if (posts.isEmpty()) {
                return;
            }
            Set<String> existingPostIds = new HashSet<>(campaignRepository
                    .findByPostIdIn(posts.stream().map(BlogPost::getId).toList())
                    .stream()
                    .map(NewsletterCampaign::getPostId)
                    .toList());
            Instant now = clock.instant();
            for (BlogPost post : posts) {
                if (existingPostIds.contains(post.getId())) {
                    continue;
                }
                Instant scheduledAt = post.getPublishedAt()
                        .plus(Duration.ofMinutes(properties.getSendDelayMinutes()));
                if (scheduledAt.isBefore(now)) {
                    scheduledAt = now;
                }
                try {
                    campaignRepository.insert(NewsletterCampaign.scheduled(
                            post.getId(),
                            scheduledAt,
                            now));
                } catch (DuplicateKeyException exception) {
                    // 동시 reconciliation winner가 이미 같은 postId Campaign을 생성했다.
                }
            }
            lastSeenId = posts.getLast().getId();
        }
    }

    @Override
    public void processNextScheduledCampaign() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant now = clock.instant();
        String claimToken = claimTokenGenerator.create();
        campaignRepository.claimNextScheduled(
                        now,
                        claimExpiresAt(now),
                        claimToken)
                .ifPresent(this::generateDeliveries);
    }

    @Override
    public void processNextStaleCampaign() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        Instant now = clock.instant();
        String claimToken = claimTokenGenerator.create();
        campaignRepository.reclaimNextExpiredGeneration(
                        now,
                        claimExpiresAt(now),
                        claimToken)
                .ifPresent(this::generateDeliveries);
    }

    @Override
    public void completeNextCampaign() {
        if (!properties.isSendingEnabled()) {
            return;
        }
        campaignRepository.findNextReadyForCompletion().ifPresent(campaign -> {
            DeliveryCounts counts = deliveryRepository.countByCampaign(
                    campaign.getId(),
                    NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS);
            if (campaign.getTotalRecipients() == null
                    || counts.total() != campaign.getTotalRecipients()
                    || counts.terminalTotal() != counts.total()) {
                campaignRepository.touchSendingCampaign(
                        campaign.getId(),
                        campaign.getClaimToken(),
                        clock.instant());
                return;
            }
            NewsletterCampaignStatus terminalStatus = counts.terminalFailed() == 0
                    ? NewsletterCampaignStatus.SENT
                    : NewsletterCampaignStatus.FAILED;
            campaignRepository.completeCampaign(
                    campaign.getId(),
                    campaign.getClaimToken(),
                    terminalStatus,
                    counts.total(),
                    counts.sent(),
                    counts.terminalFailed(),
                    counts.skipped(),
                    clock.instant());
        });
    }

    @Override
    public boolean cancelScheduledCampaign(String campaignId) {
        return campaignRepository.cancelScheduled(campaignId, clock.instant());
    }

    @Override
    public boolean reopenFailedCampaign(String campaignId) {
        return campaignRepository.reopenFailed(campaignId, clock.instant());
    }

    private void generateDeliveries(NewsletterCampaign campaign) {
        String lastSeenId = null;
        while (properties.isSendingEnabled()) {
            List<NewsletterSubscriber> subscribers = subscriberRepository.findActiveAfterId(
                    lastSeenId,
                    properties.getBatchSize());
            if (subscribers.isEmpty()) {
                DeliveryCounts counts = deliveryRepository.countByCampaign(
                        campaign.getId(),
                        NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS);
                campaignRepository.finishDeliveryGeneration(
                        campaign.getId(),
                        campaign.getClaimToken(),
                        counts.total(),
                        clock.instant());
                return;
            }
            Instant now = clock.instant();
            deliveryRepository.upsertPendingDeliveries(
                    campaign.getId(),
                    campaign.getPostId(),
                    subscribers.stream().map(NewsletterSubscriber::getId).toList(),
                    now);
            if (!campaignRepository.extendGenerationClaim(
                    campaign.getId(),
                    campaign.getClaimToken(),
                    claimExpiresAt(now),
                    now)) {
                return;
            }
            lastSeenId = subscribers.getLast().getId();
        }
    }

    private Instant claimExpiresAt(Instant now) {
        return now.plusSeconds(properties.getClaimTtlSeconds());
    }

}
