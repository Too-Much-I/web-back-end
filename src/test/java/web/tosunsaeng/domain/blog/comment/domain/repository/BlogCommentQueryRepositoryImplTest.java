package web.tosunsaeng.domain.blog.comment.domain.repository;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogCommentQueryRepositoryImplTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void visibleQueryUsesPostAndVisibleStatusOnly() {
        Query query = captureFindQuery(PageRequest.of(0, 20));

        assertThat(query.getQueryObject())
                .containsEntry("postId", "post-id")
                .containsEntry("status", CommentStatus.VISIBLE);
        assertThat(query.getQueryObject().get("status"))
                .isNotEqualTo(CommentStatus.PENDING)
                .isNotEqualTo(CommentStatus.HIDDEN);
    }

    @Test
    void queryUsesStableCreatedAtAndIdDescendingSort() {
        Query query = captureFindQuery(PageRequest.of(0, 20));

        Document sort = query.getSortObject();
        assertThat(new ArrayList<>(sort.keySet())).containsExactly("createdAt", "_id");
        assertThat(sort)
                .containsEntry("createdAt", -1)
                .containsEntry("_id", -1);
    }

    @Test
    void queryAppliesPaginationAndReturnsCountMetadata() {
        BlogComment comment = comment("comment-1");
        when(mongoTemplate.find(any(Query.class), eq(BlogComment.class)))
                .thenReturn(List.of(comment));
        when(mongoTemplate.count(any(Query.class), eq(BlogComment.class))).thenReturn(45L);
        BlogCommentQueryRepositoryImpl repository =
                new BlogCommentQueryRepositoryImpl(mongoTemplate);

        Page<BlogComment> result = repository.findVisibleCommentsByPostId(
                "post-id",
                PageRequest.of(1, 20));

        ArgumentCaptor<Query> findCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findCaptor.capture(), eq(BlogComment.class));
        assertThat(findCaptor.getValue().getSkip()).isEqualTo(20L);
        assertThat(findCaptor.getValue().getLimit()).isEqualTo(20);
        assertThat(result.getContent()).containsExactly(comment);
        assertThat(result.getTotalElements()).isEqualTo(45);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void countUsesSameFilterWithoutSortSkipOrLimit() {
        when(mongoTemplate.find(any(Query.class), eq(BlogComment.class)))
                .thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogComment.class))).thenReturn(0L);
        BlogCommentQueryRepositoryImpl repository =
                new BlogCommentQueryRepositoryImpl(mongoTemplate);

        repository.findVisibleCommentsByPostId("post-id", PageRequest.of(2, 20));

        ArgumentCaptor<Query> findCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Query> countCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(findCaptor.capture(), eq(BlogComment.class));
        verify(mongoTemplate).count(countCaptor.capture(), eq(BlogComment.class));
        Query findQuery = findCaptor.getValue();
        Query countQuery = countCaptor.getValue();
        assertThat(countQuery.getQueryObject()).isEqualTo(findQuery.getQueryObject());
        assertThat(countQuery.getSortObject()).isEmpty();
        assertThat(countQuery.getSkip()).isZero();
        assertThat(countQuery.getLimit()).isZero();
    }

    private Query captureFindQuery(PageRequest pageable) {
        when(mongoTemplate.find(any(Query.class), eq(BlogComment.class)))
                .thenReturn(List.of());
        when(mongoTemplate.count(any(Query.class), eq(BlogComment.class))).thenReturn(0L);
        BlogCommentQueryRepositoryImpl repository =
                new BlogCommentQueryRepositoryImpl(mongoTemplate);

        repository.findVisibleCommentsByPostId("post-id", pageable);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(BlogComment.class));
        return captor.getValue();
    }

    private BlogComment comment(String id) {
        Instant now = Instant.parse("2026-07-30T02:00:00Z");
        return BlogComment.builder()
                .id(id)
                .postId("post-id")
                .anonymousVisitorId("visitor-id")
                .nickname("차분한 수달")
                .avatarSeed("seed")
                .avatarImageKey("character-image/example-otter-v1.webp")
                .content("정상 댓글")
                .status(CommentStatus.VISIBLE)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
