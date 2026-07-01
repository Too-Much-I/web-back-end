package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.Question;

import java.util.List;

public interface QuestionRepository extends MongoRepository<Question, String> {
    List<Question> findByExamPaperId(String examPaperId);
}