package web.tosunsaeng.domain.newsletter.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.blog.domain.repository.BlogPostRepository;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterCampaign;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterCampaignStatus;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterClaimTokenGenerator;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRetryPolicy;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterCampaignRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryQueryRepository.DeliveryCounts;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterDeliveryRepository;
import web.tosunsaeng.domain.newsletter.domain.repository.NewsletterSubscriberRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterCampaignServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T05:00:00Z");

    @Mock
    private BlogPostRepository blogPostRepository;

    @Mock
    private NewsletterCampaignRepository campaignRepository;

    @Mock
    private NewsletterDeliveryRepository deliveryRepository;

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    private NewsletterDeliveryProperties properties;
    private NewsletterCampaignServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new NewsletterDeliveryProperties();
        properties.setSendingEnabled(true);
        properties.setBatchSize(2);
        service = new NewsletterCampaignServiceImpl(
                blogPostRepository,
                campaignRepository,
                deliveryRepository,
                subscriberRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new NewsletterClaimTokenGenerator());
    }

    @Test
    void reconciliationSchedulesPastImmediatelyAndFutureAfterDelay() {
        BlogPost past = post("post-a", NOW.minusSeconds(3_600));
        BlogPost future = post("post-b", NOW.plusSeconds(3_600));
        when(blogPostRepository.findNewsletterEligiblePostsAfter(null, 2))
                .thenReturn(List.of(past, future));
        when(blogPostRepository.findNewsletterEligiblePostsAfter("post-b", 2))
                .thenReturn(List.of());
        when(campaignRepository.findByPostIdIn(List.of("post-a", "post-b")))
                .thenReturn(List.of());

        service.reconcileCampaigns();

        ArgumentCaptor<NewsletterCampaign> captor =
                ArgumentCaptor.forClass(NewsletterCampaign.class);
        verify(campaignRepository, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus())
                .isEqualTo(NewsletterCampaignStatus.SCHEDULED);
        assertThat(captor.getAllValues().get(0).getScheduledAt()).isEqualTo(NOW);
        assertThat(captor.getAllValues().get(1).getScheduledAt())
                .isEqualTo(NOW.plusSeconds(3_600 + 15 * 60));
        assertThat(captor.getAllValues()).allSatisfy(campaign -> {
            assertThat(campaign.getCreatedAt()).isEqualTo(NOW);
            assertThat(campaign.getTotalRecipients()).isNull();
        });
    }

    @Test
    void existingCampaignAndConcurrentUniqueConflictAreBothIdempotent() {
        BlogPost existing = post("post-a", NOW.minusSeconds(100));
        BlogPost raced = post("post-b", NOW.minusSeconds(100));
        when(blogPostRepository.findNewsletterEligiblePostsAfter(null, 2))
                .thenReturn(List.of(existing, raced));
        when(blogPostRepository.findNewsletterEligiblePostsAfter("post-b", 2))
                .thenReturn(List.of());
        when(campaignRepository.findByPostIdIn(List.of("post-a", "post-b")))
                .thenReturn(List.of(campaign(
                        "campaign-existing",
                        "post-a",
                        NewsletterCampaignStatus.SENT,
                        0L)));
        doThrow(new DuplicateKeyException("postId duplicate driver detail"))
                .when(campaignRepository)
                .insert(any(NewsletterCampaign.class));

        assertThatCode(service::reconcileCampaigns).doesNotThrowAnyException();

        ArgumentCaptor<NewsletterCampaign> captor =
                ArgumentCaptor.forClass(NewsletterCampaign.class);
        verify(campaignRepository).insert(captor.capture());
        assertThat(captor.getValue().getPostId()).isEqualTo("post-b");
    }

    @Test
    void campaignClaimGeneratesActiveSubscribersByIdCursorAndFinalizesCount() {
        NewsletterCampaign claimed = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                null);
        when(campaignRepository.claimNextScheduled(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenReturn(Optional.of(claimed));
        NewsletterSubscriber a = subscriber("subscriber-a");
        NewsletterSubscriber b = subscriber("subscriber-b");
        NewsletterSubscriber c = subscriber("subscriber-c");
        when(subscriberRepository.findActiveAfterId(null, 2))
                .thenReturn(List.of(a, b));
        when(subscriberRepository.findActiveAfterId("subscriber-b", 2))
                .thenReturn(List.of(c));
        when(subscriberRepository.findActiveAfterId("subscriber-c", 2))
                .thenReturn(List.of());
        when(campaignRepository.extendGenerationClaim(
                eq("campaign-id"), eq("claim-token"), any(Instant.class), eq(NOW)))
                .thenReturn(true);
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(3, 0, 0, 0));

        service.processNextScheduledCampaign();

        verify(deliveryRepository).upsertPendingDeliveries(
                "campaign-id", "post-id", List.of("subscriber-a", "subscriber-b"), NOW);
        verify(deliveryRepository).upsertPendingDeliveries(
                "campaign-id", "post-id", List.of("subscriber-c"), NOW);
        verify(campaignRepository, times(2)).extendGenerationClaim(
                eq("campaign-id"), eq("claim-token"), eq(NOW.plusSeconds(300)), eq(NOW));
        verify(campaignRepository).finishDeliveryGeneration(
                "campaign-id", "claim-token", 3, NOW);
    }

    @Test
    void twoConcurrentSchedulerClaimsGenerateOnlyOnce() throws Exception {
        NewsletterCampaign claimed = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                null);
        AtomicBoolean winner = new AtomicBoolean();
        when(campaignRepository.claimNextScheduled(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenAnswer(invocation -> winner.compareAndSet(false, true)
                        ? Optional.of(claimed)
                        : Optional.empty());
        when(subscriberRepository.findActiveAfterId(null, 2)).thenReturn(List.of());
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(0, 0, 0, 0));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(service::processNextScheduledCampaign);
            Future<?> second = executor.submit(service::processNextScheduledCampaign);
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        verify(campaignRepository, times(2)).claimNextScheduled(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString());
        verify(campaignRepository).finishDeliveryGeneration(
                "campaign-id", "claim-token", 0, NOW);
    }

    @Test
    void expiredUnfinishedCampaignIsReclaimedWithoutStatusReset() {
        NewsletterCampaign reclaimed = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                null);
        when(campaignRepository.reclaimNextExpiredGeneration(
                eq(NOW), eq(NOW.plusSeconds(300)), anyString()))
                .thenReturn(Optional.of(reclaimed));
        when(subscriberRepository.findActiveAfterId(null, 2)).thenReturn(List.of());
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(0, 0, 0, 0));

        service.processNextStaleCampaign();

        verify(campaignRepository).finishDeliveryGeneration(
                "campaign-id", "claim-token", 0, NOW);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void completionMarksSentWhenFailuresAreZeroIncludingSkippedOnly() {
        NewsletterCampaign campaign = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                2L);
        when(campaignRepository.findNextReadyForCompletion())
                .thenReturn(Optional.of(campaign));
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(2, 0, 0, 2));

        service.completeNextCampaign();

        verify(campaignRepository).completeCampaign(
                "campaign-id",
                "claim-token",
                NewsletterCampaignStatus.SENT,
                2,
                0,
                0,
                2,
                NOW);
    }

    @Test
    void completionMarksFailedAndDoesNotLeaveTerminalFailuresSending() {
        NewsletterCampaign campaign = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                3L);
        when(campaignRepository.findNextReadyForCompletion())
                .thenReturn(Optional.of(campaign));
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(3, 1, 1, 1));

        service.completeNextCampaign();

        verify(campaignRepository).completeCampaign(
                "campaign-id",
                "claim-token",
                NewsletterCampaignStatus.FAILED,
                3,
                1,
                1,
                1,
                NOW);
    }

    @Test
    void incompleteCampaignIsRotatedButNotCompleted() {
        NewsletterCampaign campaign = campaign(
                "campaign-id",
                "post-id",
                NewsletterCampaignStatus.SENDING,
                2L);
        when(campaignRepository.findNextReadyForCompletion())
                .thenReturn(Optional.of(campaign));
        when(deliveryRepository.countByCampaign(
                "campaign-id", NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS))
                .thenReturn(new DeliveryCounts(2, 1, 0, 0));

        service.completeNextCampaign();

        verify(campaignRepository).touchSendingCampaign(
                "campaign-id", "claim-token", NOW);
        verify(campaignRepository, never()).completeCampaign(
                anyString(), anyString(), any(), anyInt(), anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    void killSwitchDoesNotClaimRecoverCompleteOrMutateSendingState() {
        properties.setSendingEnabled(false);

        service.processNextScheduledCampaign();
        service.processNextStaleCampaign();
        service.completeNextCampaign();

        verifyNoInteractions(campaignRepository, deliveryRepository, subscriberRepository);
    }

    private BlogPost post(String id, Instant publishedAt) {
        return BlogPost.builder()
                .id(id)
                .slug(id)
                .title("제목")
                .summary("요약")
                .status(BlogPostStatus.PUBLISHED)
                .publishedAt(publishedAt)
                .newsletterEnabled(true)
                .createdAt(NOW.minusSeconds(10_000))
                .updatedAt(NOW)
                .build();
    }

    private NewsletterCampaign campaign(
            String id,
            String postId,
            NewsletterCampaignStatus status,
            Long totalRecipients) {
        return NewsletterCampaign.builder()
                .id(id)
                .postId(postId)
                .status(status)
                .scheduledAt(NOW.minusSeconds(60))
                .claimedAt(NOW.minusSeconds(30))
                .claimToken("claim-token")
                .claimExpiresAt(totalRecipients == null ? NOW.minusSeconds(1) : null)
                .totalRecipients(totalRecipients)
                .createdAt(NOW.minusSeconds(120))
                .updatedAt(NOW.minusSeconds(30))
                .build();
    }

    private NewsletterSubscriber subscriber(String id) {
        return NewsletterSubscriber.builder()
                .id(id)
                .email(id + "@example.test")
                .status(NewsletterSubscriberStatus.ACTIVE)
                .tokenVersion(1)
                .consentAt(NOW)
                .subscribedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
