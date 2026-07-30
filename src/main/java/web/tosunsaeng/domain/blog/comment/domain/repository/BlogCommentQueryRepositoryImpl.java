package web.tosunsaeng.domain.blog.comment.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentStatus;

import java.util.List;

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
}
