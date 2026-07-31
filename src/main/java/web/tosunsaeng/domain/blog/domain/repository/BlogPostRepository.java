package web.tosunsaeng.domain.blog.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;

import java.util.Optional;

public interface BlogPostRepository
        extends MongoRepository<BlogPost, String>, BlogPostQueryRepository {

    Optional<BlogPost> findBySlug(String slug);
}
