package web.tosunsaeng.domain.comment.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class BlogCommentQueryRepositoryImpl implements BlogCommentQueryRepository {

    private static final Sort COMMENT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("_id"));

    private final MongoTemplate mongoTemplate;

    @Override
    public Page<BlogComment> findVisibleCommentsByPostId(String postId, Pageable pageable) {
        Criteria criteria = Criteria.where("postId").is(postId)
                .and("status").is(CommentStatus.VISIBLE);
        Query contentQuery = Query.query(criteria)
                .with(pageable)
                .with(COMMENT_SORT);
        List<BlogComment> comments = mongoTemplate.find(contentQuery, BlogComment.class);

        Query countQuery = Query.query(criteria);
        long totalElements = mongoTemplate.count(countQuery, BlogComment.class);
        return new PageImpl<>(comments, pageable, totalElements);
    }

    @Override
    public Page<BlogComment> findCommentsForModeration(
            CommentStatus status,
            String postId,
            Instant createdAtFrom,
            Instant createdAtTo,
            Pageable pageable) {
        Query contentQuery = moderationQuery(
                status, postId, createdAtFrom, createdAtTo)
                .with(pageable)
                .with(COMMENT_SORT);
        List<BlogComment> comments = mongoTemplate.find(contentQuery, BlogComment.class);

        Query countQuery = moderationQuery(status, postId, createdAtFrom, createdAtTo);
        long totalElements = mongoTemplate.count(countQuery, BlogComment.class);
        return new PageImpl<>(comments, pageable, totalElements);
    }

    @Override
    public Optional<BlogComment> hideComment(
            String commentId,
            HiddenReason reason,
            Instant now) {
        Query query = Query.query(Criteria.where("_id").is(commentId)
                .and("status").in(CommentStatus.VISIBLE, CommentStatus.PENDING));
        Update update = new Update()
                .set("status", CommentStatus.HIDDEN)
                .set("hiddenAt", now)
                .set("hiddenReason", reason)
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                BlogComment.class));
    }

    @Override
    public Optional<BlogComment> restoreComment(String commentId, Instant now) {
        Query query = Query.query(Criteria.where("_id").is(commentId)
                .and("status").is(CommentStatus.HIDDEN));
        Update update = new Update()
                .set("status", CommentStatus.VISIBLE)
                .unset("hiddenAt")
                .unset("hiddenReason")
                .set("updatedAt", now);
        return Optional.ofNullable(mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                BlogComment.class));
    }

    private Query moderationQuery(
            CommentStatus status,
            String postId,
            Instant createdAtFrom,
            Instant createdAtTo) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (postId != null) {
            query.addCriteria(Criteria.where("postId").is(postId));
        }
        if (createdAtFrom != null || createdAtTo != null) {
            Criteria createdAt = Criteria.where("createdAt");
            if (createdAtFrom != null) {
                createdAt.gte(createdAtFrom);
            }
            if (createdAtTo != null) {
                createdAt.lt(createdAtTo);
            }
            query.addCriteria(createdAt);
        }
        return query;
    }
}
