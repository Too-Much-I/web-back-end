package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import java.util.Optional;

public interface MockExamRepository extends MongoRepository<MockExam, String> {
    Optional<MockExam> findByMockExamId(String mockExamId);
}