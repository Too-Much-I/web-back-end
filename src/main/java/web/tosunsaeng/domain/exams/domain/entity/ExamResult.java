package web.tosunsaeng.domain.exams.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "exam_results")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamResult {
    @Id
    private String id;
    private String examId;

    // 요약 데이터 (문항 데이터일 때는 null)
    private Integer totalScore;
    private String levelEstimate;
    private String summary;
    private String overallFeedback;
    private Map<String, String> partFeedback;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendedPractice;

    // 문항 데이터 (요약 데이터일 때는 null)
    private Integer partNumber;
    private Integer questionNumber;
    private Double score;
    private Double maxScore;
    private String transcript;
    private ItemFeedback feedback;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemFeedback {
        private String summary;
        private String level;
        private Double pronunciationFluencyScore;
        private Double contentRelevanceScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private String pronunciation;
        private String fluency;
        private String content;
        private String grammarVocabulary;
        private List<String> actionItems;
    }
}