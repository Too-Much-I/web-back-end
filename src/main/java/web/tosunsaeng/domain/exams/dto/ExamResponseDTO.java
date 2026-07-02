package web.tosunsaeng.domain.exams.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;

import java.util.List;

public class ExamResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSessionResult {
        private String examId;
        private List<QuestionDTO> questions;
    }

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDTO {
        private String part;
        private String questionId;
        private String text;
        private Integer prepTimeSec;
        private Integer speakTimeSec;
        private String audioUrl;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadUrlResult {
        private String uploadUrl;
        private String fileKey;
        private Integer expiresIn;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitResult {
        private ExamStatus status; // (수정) String -> ExamStatus 통일
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResult {
        private String examId;
        private ExamStatus overallStatus; // (수정) String -> ExamStatus 통일
        private Integer progressPercent;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreResult {
        private String examId;
        private Integer totalScore;
        private MetricsDTO metrics;
        private List<PartResultDTO> partResults;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsDTO {
        private String pronunciation;
        private String fluency;
        private String grammar;
        private String vocabulary;
        private String topicRelevance;
    }

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartResultDTO {
        private String part;
        private String questionId;
        private String audioUrl;
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}