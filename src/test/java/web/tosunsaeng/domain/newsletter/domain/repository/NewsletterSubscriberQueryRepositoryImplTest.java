package web.tosunsaeng.domain.newsletter.domain.repository;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterSubscriberQueryRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;

    private NewsletterSubscriberQueryRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new NewsletterSubscriberQueryRepositoryImpl(mongoTemplate);
    }

    @Test
    void reactivationUsesAtomicEmailAndUnsubscribedCondition() {
        NewsletterSubscriber active = subscriber(
                NewsletterSubscriberStatus.ACTIVE, 2);
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterSubscriber.class)))
                .thenReturn(active);

        Optional<NewsletterSubscriber> result = repository.reactivateByEmail(
                "user@example.com", NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterSubscriber.class));
        assertThat(result).contains(active);
        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("email", "user@example.com")
                .containsEntry("status", NewsletterSubscriberStatus.UNSUBSCRIBED);
        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(update.get("$set", Document.class))
                .containsEntry("status", NewsletterSubscriberStatus.ACTIVE)
                .containsEntry("consentAt", NOW)
                .containsEntry("subscribedAt", NOW)
                .containsEntry("updatedAt", NOW);
        assertThat(update.get("$unset", Document.class).keySet())
                .containsExactly("unsubscribedAt");
        assertThat(update.get("$inc", Document.class))
                .containsEntry("tokenVersion", 1L);
    }

    @Test
    void unsubscribeAtomicallyAllowsActiveAndBouncedWithMatchingVersion() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterSubscriber.class)))
                .thenReturn(subscriber(NewsletterSubscriberStatus.UNSUBSCRIBED, 4));

        repository.unsubscribeByIdAndVersion("subscriber-id", 4, NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                any(FindAndModifyOptions.class),
                eq(NewsletterSubscriber.class));
        Document query = queryCaptor.getValue().getQueryObject();
        assertThat(query)
                .containsEntry("_id", "subscriber-id")
                .containsEntry("tokenVersion", 4L);
        assertThat(query.get("status"))
                .isEqualTo(new Document("$in", List.of(
                        NewsletterSubscriberStatus.ACTIVE,
                        NewsletterSubscriberStatus.BOUNCED)));
        assertThat(updateCaptor.getValue().getUpdateObject().get("$set", Document.class))
                .containsEntry("status", NewsletterSubscriberStatus.UNSUBSCRIBED)
                .containsEntry("unsubscribedAt", NOW)
                .containsEntry("updatedAt", NOW)
                .doesNotContainKey("tokenVersion");
    }

    @Test
    void conditionalMissReturnsEmptyWithoutFallbackMutation() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(NewsletterSubscriber.class)))
                .thenReturn(null);

        assertThat(repository.reactivateByEmail("user@example.com", NOW)).isEmpty();
    }

    @Test
    void activeCursorQueryExcludesUnsubscribedAndBounced() {
        when(mongoTemplate.find(any(Query.class), eq(NewsletterSubscriber.class)))
                .thenReturn(List.of());

        repository.findActiveAfterId(null, 100);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(NewsletterSubscriber.class));
        Query query = captor.getValue();
        assertThat(query.getQueryObject())
                .containsEntry("status", NewsletterSubscriberStatus.ACTIVE);
        assertThat(query.getSortObject()).isEqualTo(new Document("_id", 1));
        assertThat(query.getLimit()).isEqualTo(100);
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeCursorStartsStrictlyAfterLastSeenId() {
        when(mongoTemplate.find(any(Query.class), eq(NewsletterSubscriber.class)))
                .thenReturn(List.of());

        repository.findActiveAfterId("subscriber-100", 20);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(NewsletterSubscriber.class));
        List<Document> clauses = (List<Document>) captor.getValue()
                .getQueryObject()
                .get("$and");
        assertThat(clauses.get(0))
                .containsEntry("status", NewsletterSubscriberStatus.ACTIVE);
        assertThat(clauses.get(1).get("_id"))
                .isEqualTo(new Document("$gt", "subscriber-100"));
        assertThat(captor.getValue().getLimit()).isEqualTo(20);
    }

    @Test
    void activeCursorRejectsNonPositiveBatchWithoutMongoCall() {
        assertThat(repository.findActiveAfterId(null, 0)).isEmpty();

        verifyNoInteractions(mongoTemplate);
    }

    private NewsletterSubscriber subscriber(
            NewsletterSubscriberStatus status,
            long tokenVersion) {
        return NewsletterSubscriber.builder()
                .id("subscriber-id")
                .email("user@example.com")
                .status(status)
                .tokenVersion(tokenVersion)
                .consentAt(NOW)
                .subscribedAt(NOW)
                .unsubscribedAt(status == NewsletterSubscriberStatus.UNSUBSCRIBED
                        ? NOW : null)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
