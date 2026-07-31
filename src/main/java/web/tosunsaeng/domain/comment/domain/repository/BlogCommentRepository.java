package web.tosunsaeng.domain.comment.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;

public interface BlogCommentRepository
        extends MongoRepository<BlogComment, String>, BlogCommentQueryRepository {
}
