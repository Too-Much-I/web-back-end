package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "exam_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResult {
    @Id
    private String id;
    private String examId; // Redis 세션과 동일한 식별자
    private Integer totalScore; // 명칭 및 타입 통일
    private String feedback;
    private Metrics metrics;
    private List<PartResult> partResults;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Metrics {
        private String pronunciation;
        private String fluency;
        private String grammar;
        private String vocabulary;
        private String topicRelevance;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PartResult {
        private Integer part;
        private String questionId; // 음성 파일 매칭을 위한 고유 식별자
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}