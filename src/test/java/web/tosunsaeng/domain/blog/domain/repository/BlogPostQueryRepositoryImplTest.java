package web.tosunsaeng.domain.blog.domain.repository;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPostQueryRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-07-29T08:00:00Z");

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void publicCriteriaContainsAllPublicationConditions() {
        Query query = capturePublicListQuery(PageRequest.of(0, 10));

        Document criteria = query.getQueryObject();
        assertThat(criteria.get("status")).isEqualTo(BlogPostStatus.PUBLISHED);
        assertPublishedAtCriteria(criteria);
    }

    @Test
    void publicCriteriaExcludesDraft() {
        Document criteria = capturePublicListQuery(PageRequest.of(0, 10)).getQueryObject();

        assertThat(criteria.get("status"))
                .isEqualTo(BlogPostStatus.PUBLISHED)
                .isNotEqualTo(BlogPostStatus.DRAFT);
    }

    @Test
    void publicCriteriaExcludesArchived() {
        Document criteria = capturePublicListQuery(PageRequest.of(0, 10)).getQueryObject();

        assertThat(criteria.get("status"))
                .isEqualTo(BlogPostStatus.PUBLISHED)
                .isNotEqualTo(BlogPostStatus.ARCHIVED);
    }

    @Test
    void publicCriteriaExcludesFuturePublication() {
        Document publishedAt = publishedAtCriteria(
                capturePublicListQuery(PageRequest.of(0, 10)).getQueryObject());

        assertThat(publishedAt.get("$lte")).isEqualTo(NOW);
        assertThat(publishedAt.containsKey("$gt")).isFalse();
    }

    @Test
    void publicCriteriaExplicitlyExcludesNullPublishedAt() {
        Document publishedAt = publishedAtCriteria(
                capturePublicListQuery(PageRequest.of(0, 10)).getQueryObject());

        assertThat(publishedAt.getBoolean("$exists")).isTrue();
        assertThat(publishedAt.containsKey("$ne")).isTrue();
        assertThat(publishedAt.get("$ne")).isNull();
    }

    @Test
    void publicCriteriaUsesInclusivePublishedAtBoundary() {
        Document publishedAt = publishedAtCriteria(
                capturePublicListQuery(PageRequest.of(0, 10)).getQueryObject());

        assertThat(publishedAt).containsEntry("$lte", NOW);
        assertThat(publishedAt.containsKey("$lt")).isFalse();
    }

    @Test
    void listUsesStableSort() {
        Query query = capturePublicListQuery(PageRequest.of(0, 10));

        assertStableSort(query.getSortObject());
    }

    @Test
    void listAppliesPagination() {
        Query query = capturePublicListQuery(PageRequest.of(2, 10));

        assertThat(query.getSkip()).isEqualTo(20L);
        assertThat(query.getLimit()).isEqualTo(10);
    }

    @Test
    void listCountUsesSameFilterWithoutPagingOrSort() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogPost.class))).thenReturn(23L);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findPublicPosts(NOW, PageRequest.of(1, 10));

        ArgumentCaptor<Query> findCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> countCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findCaptor.capture(), eq(BlogPost.class));
        verify(mongoTemplate).count(countCaptor.capture(), eq(BlogPost.class));
        assertThat(countCaptor.getValue().getQueryObject())
                .isEqualTo(findCaptor.getValue().getQueryObject());
        assertThat(countCaptor.getValue().getSkip()).isZero();
        assertThat(countCaptor.getValue().getLimit()).isZero();
        assertThat(countCaptor.getValue().getSortObject()).isEmpty();
    }

    @Test
    void detailUsesSlugAndSamePublicCriteria() {
        when(mongoTemplate.findOne(any(Query.class), eq(BlogPost.class))).thenReturn(null);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findPublicPostBySlug("public-post", NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(queryCaptor.capture(), eq(BlogPost.class));
        List<Document> clauses = andClauses(queryCaptor.getValue().getQueryObject());
        assertThat(clauses.get(0).get("status")).isEqualTo(BlogPostStatus.PUBLISHED);
        assertPublishedAtCriteria(clauses.get(0));
        assertThat(clauses.get(1)).containsEntry("slug", "public-post");
    }

    @Test
    void detailViewCountIncrementUsesPublicSlugCriteriaAndOnlyAtomicIncrement() {
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(BlogPost.class)))
                .thenReturn(null);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        assertThat(repository.findPublicPostBySlugAndIncrementViewCount("public-post", NOW))
                .isEmpty();

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> optionsCaptor =
                ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(
                queryCaptor.capture(),
                updateCaptor.capture(),
                optionsCaptor.capture(),
                eq(BlogPost.class));

        List<Document> clauses = andClauses(queryCaptor.getValue().getQueryObject());
        assertThat(clauses.get(0).get("status")).isEqualTo(BlogPostStatus.PUBLISHED);
        assertPublishedAtCriteria(clauses.get(0));
        assertThat(clauses.get(1)).containsEntry("slug", "public-post");

        Document update = updateCaptor.getValue().getUpdateObject();
        assertThat(update.keySet()).containsExactly("$inc");
        assertThat(update.get("$inc", Document.class))
                .containsExactlyEntriesOf(new Document("viewCount", 1L));
        assertThat(optionsCaptor.getValue().isReturnNew()).isTrue();
        assertThat(optionsCaptor.getValue().isUpsert()).isFalse();
    }

    @Test
    void searchQuotesRegexSpecialCharacters() {
        Pattern pattern = captureSearchPattern("토익.*[1]");

        assertThat(pattern.pattern()).isEqualTo(Pattern.quote("토익.*[1]"));
    }

    @Test
    void searchUsesCaseInsensitiveRegex() {
        Pattern pattern = captureSearchPattern("Speaking");

        assertThat(pattern.flags() & Pattern.CASE_INSENSITIVE)
                .isEqualTo(Pattern.CASE_INSENSITIVE);
    }

    @Test
    void searchUsesPublicCriteriaStableSortPaginationAndMatchingCount() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogPost.class))).thenReturn(0L);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.searchPublicPostsByTitle("토익", NOW, PageRequest.of(1, 10));

        ArgumentCaptor<Query> findCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> countCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findCaptor.capture(), eq(BlogPost.class));
        verify(mongoTemplate).count(countCaptor.capture(), eq(BlogPost.class));
        Query findQuery = findCaptor.getValue();
        List<Document> clauses = andClauses(findQuery.getQueryObject());
        assertThat(clauses.get(0).get("status")).isEqualTo(BlogPostStatus.PUBLISHED);
        assertPublishedAtCriteria(clauses.get(0));
        assertStableSort(findQuery.getSortObject());
        assertThat(findQuery.getSkip()).isEqualTo(10L);
        assertThat(findQuery.getLimit()).isEqualTo(10);
        assertThat(countCaptor.getValue().getQueryObject()).isEqualTo(findQuery.getQueryObject());
        assertThat(countCaptor.getValue().getSortObject()).isEmpty();
        assertThat(countCaptor.getValue().getSkip()).isZero();
        assertThat(countCaptor.getValue().getLimit()).isZero();
    }

    @Test
    void requestedRelatedSlugsUseSingleInQueryWithPublicCriteria() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findPublicPostsBySlugs(List.of("a", "b", "c"), NOW);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(BlogPost.class));
        List<Document> clauses = andClauses(queryCaptor.getValue().getQueryObject());
        assertThat(clauses.get(0).get("status")).isEqualTo(BlogPostStatus.PUBLISHED);
        assertThat(((Document) clauses.get(1).get("slug")).get("$in"))
                .isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void latestRelatedQueryExcludesSelectedSlugsAndUsesStableSortAndLimit() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findLatestPublicPostsExcluding(List.of("current", "selected"), NOW, 2);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(BlogPost.class));
        Query query = queryCaptor.getValue();
        List<Document> clauses = andClauses(query.getQueryObject());
        assertThat(((Document) clauses.get(1).get("slug")).get("$nin"))
                .isEqualTo(List.of("current", "selected"));
        assertStableSort(query.getSortObject());
        assertThat(query.getLimit()).isEqualTo(2);
    }

    @Test
    void newsletterEligibilityRequiresPublishedNonNullAndExplicitOptIn() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findNewsletterEligiblePostsAfter(null, 100);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(BlogPost.class));
        Document criteria = captor.getValue().getQueryObject();
        assertThat(criteria)
                .containsEntry("status", BlogPostStatus.PUBLISHED)
                .containsEntry("newsletterEnabled", true);
        Document publishedAt = criteria.get("publishedAt", Document.class);
        assertThat(publishedAt)
                .containsEntry("$exists", true)
                .containsKey("$ne");
        assertThat(publishedAt.get("$ne")).isNull();
        assertThat(publishedAt).doesNotContainKeys("$lte", "$lt");
        assertThat(captor.getValue().getSortObject()).isEqualTo(new Document("_id", 1));
        assertThat(captor.getValue().getLimit()).isEqualTo(100);
    }

    @Test
    void newsletterEligibilityUsesStableIdCursor() {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findNewsletterEligiblePostsAfter("post-010", 25);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(BlogPost.class));
        List<Document> clauses = andClauses(captor.getValue().getQueryObject());
        assertThat(clauses.get(0))
                .containsEntry("status", BlogPostStatus.PUBLISHED)
                .containsEntry("newsletterEnabled", true);
        assertThat(clauses.get(1).get("_id"))
                .isEqualTo(new Document("$gt", "post-010"));
        assertThat(captor.getValue().getLimit()).isEqualTo(25);
    }

    @Test
    void newsletterEligibilityRejectsNonPositiveBatchWithoutMongoCall() {
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        assertThat(repository.findNewsletterEligiblePostsAfter(null, 0)).isEmpty();

        verifyNoInteractions(mongoTemplate);
    }

    private Query capturePublicListQuery(PageRequest pageRequest) {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogPost.class))).thenReturn(0L);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.findPublicPosts(NOW, pageRequest);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(BlogPost.class));
        return queryCaptor.getValue();
    }

    private Pattern captureSearchPattern(String searchQuery) {
        when(mongoTemplate.find(any(Query.class), eq(BlogPost.class))).thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogPost.class))).thenReturn(0L);
        BlogPostQueryRepositoryImpl repository = new BlogPostQueryRepositoryImpl(mongoTemplate);

        repository.searchPublicPostsByTitle(searchQuery, NOW, PageRequest.of(0, 10));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(BlogPost.class));
        List<Document> clauses = andClauses(queryCaptor.getValue().getQueryObject());
        return (Pattern) clauses.get(1).get("title");
    }

    @SuppressWarnings("unchecked")
    private List<Document> andClauses(Document criteria) {
        return (List<Document>) criteria.get("$and");
    }

    private void assertPublishedAtCriteria(Document criteria) {
        Document publishedAt = publishedAtCriteria(criteria);
        assertThat(publishedAt.getBoolean("$exists")).isTrue();
        assertThat(publishedAt.containsKey("$ne")).isTrue();
        assertThat(publishedAt.get("$ne")).isNull();
        assertThat(publishedAt.get("$lte")).isEqualTo(NOW);
    }

    private Document publishedAtCriteria(Document criteria) {
        return (Document) criteria.get("publishedAt");
    }

    private void assertStableSort(Document sort) {
        assertThat(new ArrayList<>(sort.keySet()))
                .containsExactly("publishedAt", "createdAt", "_id");
        assertThat(sort)
                .containsEntry("publishedAt", -1)
                .containsEntry("createdAt", -1)
                .containsEntry("_id", -1);
    }
}
