package web.tosunsaeng.domain.newsletter.config;

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
import web.tosunsaeng.domain.newsletter.domain.entity.NewsletterSubscriber;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterMongoIndexInitializerTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private IndexOperations indexOperations;

    @Test
    void ensuresUniqueEmailAndStatusIndexesIdempotently() throws Exception {
        when(mongoTemplate.indexOps(NewsletterSubscriber.class))
                .thenReturn(indexOperations);
        NewsletterMongoIndexInitializer initializer =
                new NewsletterMongoIndexInitializer(mongoTemplate);

        initializer.run(null);
        initializer.run(null);

        ArgumentCaptor<IndexDefinition> captor =
                ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(4)).ensureIndex(captor.capture());
        List<IndexDefinition> definitions = captor.getAllValues();
        assertEmailIndex(definitions.get(0));
        assertStatusIndex(definitions.get(1));
        assertThat(definitions.get(2).getIndexKeys())
                .isEqualTo(definitions.get(0).getIndexKeys());
        assertThat(definitions.get(3).getIndexKeys())
                .isEqualTo(definitions.get(1).getIndexKeys());
    }

    @Test
    void indexCreationFailureIsNotSwallowed() {
        when(mongoTemplate.indexOps(NewsletterSubscriber.class))
                .thenReturn(indexOperations);
        doThrow(new IllegalStateException("index conflict"))
                .when(indexOperations)
                .ensureIndex(any(IndexDefinition.class));
        NewsletterMongoIndexInitializer initializer =
                new NewsletterMongoIndexInitializer(mongoTemplate);

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("index conflict");
    }

    @Test
    void initializerIsDisabledForTestProfile() {
        Profile profile = NewsletterMongoIndexInitializer.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("!test");
    }

    private void assertEmailIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("email", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(NewsletterMongoIndexInitializer.EMAIL_INDEX_NAME);
        assertThat(definition.getIndexOptions().getBoolean("unique")).isTrue();
    }

    private void assertStatusIndex(IndexDefinition definition) {
        assertThat(definition.getIndexKeys()).isEqualTo(new Document("status", 1));
        assertThat(definition.getIndexOptions().getString("name"))
                .isEqualTo(NewsletterMongoIndexInitializer.STATUS_INDEX_NAME);
        assertThat(definition.getIndexOptions().containsKey("unique")).isFalse();
    }
}
