package web.tosunsaeng.domain.exams.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;
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
        private String referenceText;

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
        private Map<String, Double> partScores;
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
        private Double score;
        private Double maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;
        private AzureFeedbackDTO azureFeedback;
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

    @Data
    @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureFeedbackDTO {
        private List<AzureSpokenWordDTO> spokenWordSequence;
        private List<AzureRepeatedWordEventDTO> repeatedWordEvents;
        private AzureErrorCountsDTO errorCounts;
        private AzureLegendDTO legend;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureSpokenWordDTO {
        private Integer index;
        private String word;
        private String normalizedWord;
        private String errorType;
        private Double accuracyScore;
        private Double startSeconds;
        private Double durationSeconds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureRepeatedWordEventDTO {
        private String word;
        private String normalizedWord;
        private Integer firstIndex;
        private Integer secondIndex;
        private List<String> interveningWords;
        private Double firstAccuracyScore;
        private Double secondAccuracyScore;
        private String firstErrorType;
        private String secondErrorType;
        private Double startSeconds;
        private Double secondStartSeconds;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureErrorCountsDTO {
        private Integer mispronunciation;
        private Integer omission;
        private Integer insertion;
        private Integer unnecessaryPause;
    }

    @Data
    @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureLegendDTO {
        private String correct;
        private String mispronunciation;
        private String omission;
        private String insertion;
        private String unnecessaryPause;
    }
}