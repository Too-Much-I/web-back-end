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
import java.util.Map;

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

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class SummaryResult {
        private String examId;
        private Integer totalScore;
        private String levelEstimate;
        private String summary;
        private String overallFeedback;
        private Map<String, String> partFeedback;
        private List<String> strengths;
        private List<String> weaknesses;
        private List<String> recommendedPractice;
    }

    // 💡 2-2. 개별 문항 리스트 전용 응답 DTO
    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class QuestionResult {
        private String examId;
        private PartResultDTO question;
    }

    @Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PartResultDTO {
        private Integer partNumber;
        private Integer questionNumber;
        private String audioUrl;
        private Integer score;
        private Integer maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;

    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class ItemFeedbackDTO {
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