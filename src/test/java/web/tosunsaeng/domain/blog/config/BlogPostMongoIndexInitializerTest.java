package web.tosunsaeng.domain.blog.config;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlogPostMongoIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @Test
    void ensuresNamedIndexesAndIsSafeToRunRepeatedly() throws Exception {
        when(mongoTemplate.indexOps(BlogPost.class)).thenReturn(indexOperations);
        BlogPostMongoIndexInitializer initializer = new BlogPostMongoIndexInitializer(mongoTemplate);

        initializer.run(null);
        initializer.run(null);

        ArgumentCaptor<IndexDefinition> indexCaptor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(6)).ensureIndex(indexCaptor.capture());
        List<IndexDefinition> definitions = indexCaptor.getAllValues();

        assertSlugIndex(definitions.get(0));
        assertPublicationIndex(definitions.get(1));
        assertNewsletterReconciliationIndex(definitions.get(2));
        assertThat(definitions.get(3).getIndexKeys()).isEqualTo(definitions.get(0).getIndexKeys());
        assertThat(definitions.get(4).getIndexKeys()).isEqualTo(definitions.get(1).getIndexKeys());
        assertThat(definitions.get(5).getIndexKeys()).isEqualTo(definitions.get(2).getIndexKeys());
    }

    private void assertSlugIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("slug", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(BlogPostMongoIndexInitializer.SLUG_INDEX_NAME);
        assertThat(definition.getIndexOptions().getBoolean("unique")).isTrue();
    }

    private void assertPublicationIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys())
                .isEqualTo(new Document("status", 1).append("publishedAt", -1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(BlogPostMongoIndexInitializer.PUBLICATION_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }

    private void assertNewsletterReconciliationIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("status", 1)
                .append("newsletterEnabled", 1)
                .append("publishedAt", 1)
                .append("_id", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(BlogPostMongoIndexInitializer.NEWSLETTER_RECONCILIATION_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }
}
