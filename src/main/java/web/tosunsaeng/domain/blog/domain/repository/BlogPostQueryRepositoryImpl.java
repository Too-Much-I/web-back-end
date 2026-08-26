package web.tosunsaeng.domain.blog.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class BlogPostQueryRepositoryImpl implements BlogPostQueryRepository {

    private static final Sort PUBLIC_POST_SORT = Sort.by(
            Sort.Order.desc("publishedAt"),
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<BlogPost> findPublicPosts(Instant now, Pageable pageable) {
        Criteria criteria = publicCriteria(now);
        Query contentQuery = Query.query(criteria)
                .with(pageable)
                .with(PUBLIC_POST_SORT);
        List<BlogPost> posts = mongoTemplate.find(contentQuery, BlogPost.class);

        Query countQuery = Query.query(criteria);
        long totalElements = mongoTemplate.count(countQuery, BlogPost.class);

        return new PageImpl<>(posts, pageable, totalElements);
    }

    @Override
    public Optional<BlogPost> findPublicPostBySlug(String slug, Instant now) {
        Criteria criteria = publicSlugCriteria(slug, now);
        return Optional.ofNullable(mongoTemplate.findOne(Query.query(criteria), BlogPost.class));
    }

    @Override
    public Optional<BlogPost> findPublicPostBySlugAndIncrementViewCount(
            String slug,
            Instant now) {
        Query query = Query.query(publicSlugCriteria(slug, now));
        Update update = new Update().inc("viewCount", 1L);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                options,
                BlogPost.class));
    }

    @Override
    public Page<BlogPost> searchPublicPostsByTitle(
            String query,
            Instant now,
            Pageable pageable) {
        Criteria criteria = searchCriteria(query, now);
        Query contentQuery = Query.query(criteria)
                .with(pageable)
                .with(PUBLIC_POST_SORT);
        List<BlogPost> posts = mongoTemplate.find(contentQuery, BlogPost.class);

        Query countQuery = Query.query(criteria);
        long totalElements = mongoTemplate.count(countQuery, BlogPost.class);

        return new PageImpl<>(posts, pageable, totalElements);
    }

    @Override
    public List<BlogPost> findPublicPostsBySlugs(Collection<String> slugs, Instant now) {
        if (slugs.isEmpty()) {
            return List.of();
        }

        Criteria criteria = new Criteria().andOperator(
                publicCriteria(now),
                Criteria.where("slug").in(slugs));
        return mongoTemplate.find(Query.query(criteria), BlogPost.class);
    }

    @Override
    public List<BlogPost> findLatestPublicPostsExcluding(
            Collection<String> excludedSlugs,
            Instant now,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }

        Criteria criteria = new Criteria().andOperator(
                publicCriteria(now),
                Criteria.where("slug").nin(excludedSlugs));
        Query query = Query.query(criteria)
                .with(PUBLIC_POST_SORT)
                .limit(limit);
        return mongoTemplate.find(query, BlogPost.class);
    }

    @Override
    public List<BlogPost> findNewsletterEligiblePostsAfter(
            String lastSeenId,
            int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Criteria eligible = Criteria.where("status").is(BlogPostStatus.PUBLISHED)
                .and("publishedAt").exists(true).ne(null)
                .and("newsletterEnabled").is(true);
        Criteria criteria = lastSeenId == null
                ? eligible
                : new Criteria().andOperator(
                        eligible,
                        Criteria.where("_id").gt(lastSeenId));
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.asc("_id")))
                .limit(limit);
        return mongoTemplate.find(query, BlogPost.class);
    }

    private Criteria publicCriteria(Instant now) {
        return Criteria.where("status").is(BlogPostStatus.PUBLISHED)
                .and("publishedAt").exists(true).ne(null).lte(now);
    }

    private Criteria publicSlugCriteria(String slug, Instant now) {
        return new Criteria().andOperator(
                publicCriteria(now),
                Criteria.where("slug").is(slug));
    }

    private Criteria searchCriteria(String query, Instant now) {
        Pattern literalCaseInsensitivePattern = Pattern.compile(
                Pattern.quote(query),
                Pattern.CASE_INSENSITIVE);
        return new Criteria().andOperator(
                publicCriteria(now),
                Criteria.where("title").regex(literalCaseInsensitivePattern));
    }
}
