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

    @Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionDTO {
        private Integer part;
        private Integer questionNumber;
        private String text;
        private String referenceText;
        private String partIntroText;
        private String audioUrl;
        private String guideAudioUrl;
        private String imageUrl;
        private Question.TableContext tableContext;
        private Integer prepTimeSec;
        private Integer speakTimeSec;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class UploadUrlResult {
        private String uploadUrl;
        private String fileKey;
        private Integer expiresIn;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class SubmitResult {
        private ExamStatus status;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class StatusResult {
        private String examId;
        private ExamStatus overallStatus;
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
        private Integer retryCount;
        private Integer totalRetryCount;
        private String audioUrl;
        private Double score;
        private Double maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;
        private AzureFeedbackDTO azureFeedback;
        private List<SpokenWordDTO> spokenWordSequence;
        private QuestionDTO questionInfo;
    }

    @Builder @Getter @Setter @NoArgsConstructor @AllArgsConstructor
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
        // 추가된 피드백 필드
        private List<CorrectionItemDTO> correctionItems;
        private List<String> offTopicItems;
        private String correctedAnswer;
        private String recommendedAnswer;
        private String nextStrategy;
    }

    // 추가된 교정 및 단어 클래스
    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CorrectionItemDTO {
        private String type;
        private String original;
        private String issue;
        private String explanation;
        private String suggested;
        private String severity;
    }

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SpokenWordDTO {
        private Integer index;
        private Integer segmentIndex;
        private Integer wordIndex;
        private String word;
        private Long offset;
        private Long duration;
        private Double accuracyScore;
        private Double pronunciationScore;
        private String errorType;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
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

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class AzureLegendDTO {
        private String correct;
        private String mispronunciation;
        private String omission;
        private String insertion;
        private String unnecessaryPause;
    }

    @Builder @Getter @NoArgsConstructor @AllArgsConstructor
    public static class QuestionPollResult {
        private String examId;
        private Integer questionNumber;
        private Integer retryCount;
        private ExamStatus status; // PENDING, PROCESSING, COMPLETED, FAILED
    }
}