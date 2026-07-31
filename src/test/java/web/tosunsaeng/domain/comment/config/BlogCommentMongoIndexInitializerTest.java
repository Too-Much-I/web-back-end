package web.tosunsaeng.domain.comment.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogCommentMongoIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations visitorIndexOperations;

    @Mock
    private IndexOperations commentIndexOperations;

    @Test
    void ensuresApprovedNamedIndexesAndIsSafeToRunRepeatedly() throws Exception {
        when(mongoTemplate.indexOps(AnonymousVisitor.class))
                .thenReturn(visitorIndexOperations);
        when(mongoTemplate.indexOps(BlogComment.class))
                .thenReturn(commentIndexOperations);
        BlogCommentMongoIndexInitializer initializer =
                new BlogCommentMongoIndexInitializer(mongoTemplate);

        initializer.run(null);
        initializer.run(null);

        ArgumentCaptor<IndexDefinition> visitorCaptor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        ArgumentCaptor<IndexDefinition> commentCaptor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        verify(visitorIndexOperations, times(2)).ensureIndex(visitorCaptor.capture());
        verify(commentIndexOperations, times(6)).ensureIndex(commentCaptor.capture());

        List<IndexDefinition> visitorDefinitions = visitorCaptor.getAllValues();
        assertVisitorIndex(visitorDefinitions.get(0));
        assertThat(visitorDefinitions.get(1).getIndexKeys())
                .isEqualTo(visitorDefinitions.get(0).getIndexKeys());
        assertThat(visitorDefinitions.get(1).getIndexOptions())
                .isEqualTo(visitorDefinitions.get(0).getIndexOptions());

        List<IndexDefinition> commentDefinitions = commentCaptor.getAllValues();
        assertPostStatusCreatedAtIndex(commentDefinitions.get(0));
        assertVisitorLookupIndex(commentDefinitions.get(1));
        assertStatusCreatedAtIndex(commentDefinitions.get(2));
        assertThat(commentDefinitions.get(3).getIndexKeys())
                .isEqualTo(commentDefinitions.get(0).getIndexKeys());
        assertThat(commentDefinitions.get(3).getIndexOptions())
                .isEqualTo(commentDefinitions.get(0).getIndexOptions());
        assertThat(commentDefinitions.get(4).getIndexKeys())
                .isEqualTo(commentDefinitions.get(1).getIndexKeys());
        assertThat(commentDefinitions.get(4).getIndexOptions())
                .isEqualTo(commentDefinitions.get(1).getIndexOptions());
        assertThat(commentDefinitions.get(5).getIndexKeys())
                .isEqualTo(commentDefinitions.get(2).getIndexKeys());
        assertThat(commentDefinitions.get(5).getIndexOptions())
                .isEqualTo(commentDefinitions.get(2).getIndexOptions());
    }

    @Test
    void doesNotSwallowIndexCreationFailure() {
        when(mongoTemplate.indexOps(AnonymousVisitor.class))
                .thenReturn(visitorIndexOperations);
        doThrow(new IllegalStateException("index conflict"))
                .when(visitorIndexOperations)
                .ensureIndex(any(IndexDefinition.class));
        BlogCommentMongoIndexInitializer initializer =
                new BlogCommentMongoIndexInitializer(mongoTemplate);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("index conflict");
    }

    @Test
    void isDisabledForTestProfile() {
        Profile profile = BlogCommentMongoIndexInitializer.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!test");
    }

    private void assertVisitorIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("tokenHash", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(BlogCommentMongoIndexInitializer.VISITOR_TOKEN_HASH_INDEX_NAME);
        assertThat(definition.getIndexOptions().getBoolean("unique")).isTrue();
    }

    private void assertPostStatusCreatedAtIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys())
                .isEqualTo(new Document("postId", 1)
                        .append("status", 1)
                        .append("createdAt", -1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(
                        BlogCommentMongoIndexInitializer
                                .COMMENT_POST_STATUS_CREATED_AT_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }

    private void assertVisitorLookupIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys())
                .isEqualTo(new Document("anonymousVisitorId", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(BlogCommentMongoIndexInitializer.COMMENT_VISITOR_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }

    private void assertStatusCreatedAtIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys())
                .isEqualTo(new Document("status", 1).append("createdAt", -1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(
                        BlogCommentMongoIndexInitializer
                                .COMMENT_STATUS_CREATED_AT_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }
}
