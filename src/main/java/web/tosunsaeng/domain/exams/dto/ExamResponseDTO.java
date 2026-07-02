package web.tosunsaeng.domain.exams.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;

import java.util.List;

public class ExamResponseDTO {

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CreateSessionResult {
        private String examId;
        private String title;
        private List<QuestionDTO> questions;
    }

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionDTO {
        private Integer part;
        private Integer questionNumber;

        // 텍스트 기반 문제 (Part 1, 3, 5 등)
        private String text;

        // 오디오 파일 (Part 1 등에서 문제 읽어줄 때 사용)
        private String audioUrl;

        // 이미지 문제 (Part 2)
        private String imageUrl;

        // 표 문제 (Part 4)
        private Question.TableContext tableContext;

        // 시간 정보
        private Integer prepTimeSec;
        private Integer speakTimeSec;
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
        private Integer part;
        private String questionId;
        private String audioUrl;
        private String sttText;
        private String deductionReason;
        private String etsRubric;
        private String feedback;
    }
}