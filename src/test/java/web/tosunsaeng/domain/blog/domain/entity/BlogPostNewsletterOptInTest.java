package web.tosunsaeng.domain.blog.domain.entity;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.assertThat;

class BlogPostNewsletterOptInTest {

    private MappingMongoConverter converter;

    @BeforeEach
    void setUp() {
        MongoCustomConversions conversions = MongoCustomConversions.create(
                adapter -> {
                });
        MongoMappingContext mappingContext = new MongoMappingContext();
        mappingContext.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mappingContext.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mappingContext);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    @Test
    void missingMongoFieldMapsToFalse() {
        BlogPost post = converter.read(
                BlogPost.class,
                new Document("_id", "post-id")
                        .append("slug", "existing-post"));

        assertThat(post.isNewsletterEnabled()).isFalse();
    }

    @Test
    void onlyExplicitTrueOptsIn() {
        BlogPost post = converter.read(
                BlogPost.class,
                new Document("_id", "post-id")
                        .append("slug", "enabled-post")
                        .append("newsletterEnabled", true));

        assertThat(post.isNewsletterEnabled()).isTrue();
    }
}
