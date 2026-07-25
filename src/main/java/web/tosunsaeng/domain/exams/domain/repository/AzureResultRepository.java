package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.AzureResult;

import java.util.Optional;

public interface AzureResultRepository extends MongoRepository<AzureResult, String> {
    Optional<AzureResult> findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(String examId, Integer questionNumber, Integer retryCount);}