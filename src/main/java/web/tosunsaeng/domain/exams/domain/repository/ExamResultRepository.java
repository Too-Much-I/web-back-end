package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import java.util.Optional;

public interface ExamResultRepository extends MongoRepository<ExamResult, String> {
    Optional<ExamResult> findByExamId(String examId); // examId로 결과 찾기
}