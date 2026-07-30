package web.tosunsaeng.domain.blog.comment.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;

public interface BlogCommentRepository
        extends MongoRepository<BlogComment, String>, BlogCommentQueryRepository {
}
