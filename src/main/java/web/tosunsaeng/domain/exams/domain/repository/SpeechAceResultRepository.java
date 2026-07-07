package web.tosunsaeng.domain.exams.domain.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import web.tosunsaeng.domain.exams.domain.entity.SpeechAceResult;

public interface SpeechAceResultRepository extends MongoRepository<SpeechAceResult, String> {
}