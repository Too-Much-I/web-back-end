package web.tosunsaeng.domain.comment.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.comment.domain.entity.AnonymousVisitor;

import java.util.Optional;

public interface AnonymousVisitorRepository extends MongoRepository<AnonymousVisitor, String> {

    Optional<AnonymousVisitor> findByTokenHash(String tokenHash);
}
