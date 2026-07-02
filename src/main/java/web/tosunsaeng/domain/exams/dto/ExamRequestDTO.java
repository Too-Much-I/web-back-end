package web.tosunsaeng.domain.exams.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

public class ExamRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SubmitAudioReq {
        private String fileKey;
    }

    @Getter
    @NoArgsConstructor
    public static class AiResultReq {
        private String examId;
        private Integer totalScore;
        private String feedback;
        private MetricsDTO metrics;
        private List<PartResultDTO> partResults;
    }

    @Getter
    @NoArgsConstructor
    public static class MetricsDTO {
        private String pronunciation;
        private String fluency;
        private String grammar;
        private String vocabulary;
        private String topicRelevance;
    }

    @Getter
    @NoArgsConstructor
    public static class PartResultDTO {
        private Integer part;
        private String questionId;
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}