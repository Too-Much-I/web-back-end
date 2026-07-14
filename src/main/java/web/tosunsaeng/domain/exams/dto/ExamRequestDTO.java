package web.tosunsaeng.domain.exams.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

public class ExamRequestDTO {

    @Getter
    @NoArgsConstructor
    public static class SubmitAudioReq {
        private String fileKey;
    }

    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiResultReq {
        @JsonProperty("user_id") private String examId;
        @JsonProperty("mock_exam_id") private String mockExamId;

        // 요약 데이터
        @JsonProperty("suggested_total_score") private Integer totalScore;
        @JsonProperty("level_estimate") private String levelEstimate;
        private String summary;
        @JsonProperty("overall_feedback") private String overallFeedback;
        @JsonProperty("part_feedback") private Map<String, String> partFeedback;
        private List<String> strengths;
        private List<String> weaknesses;
        @JsonProperty("recommended_practice") private List<String> recommendedPractice;

        // 문항 데이터
        @JsonProperty("part_number") private Integer partNumber;
        @JsonProperty("question_number") private Integer questionNumber;
        @JsonProperty("retry_count") private Integer retryCount;
        private Double score;
        @JsonProperty("max_score") private Double maxScore;
        private String transcript;
        private ItemFeedbackDTO feedback;

        // 새로 추가된 최상단 필드 (파트 1 전용)
        @JsonProperty("spoken_word_sequence") private List<SpokenWordDTO> spokenWordSequence;
    }

    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpeechAceReq {
        @JsonProperty("user_id") private String examId;
        @JsonProperty("question_number") private Integer questionNumber;
        @JsonProperty("retry-count") private Integer retryCount;
        @JsonProperty("speechace_result") private Map<String, Object> speechAceData;
    }

    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemFeedbackDTO {
        private String summary;
        private String level;
        @JsonProperty("pronunciation_fluency_score") private Double pronunciationFluencyScore;
        @JsonProperty("content_relevance_score") private Double contentRelevanceScore;
        private List<String> strengths;
        private List<String> weaknesses;
        private String pronunciation;
        private String fluency;
        private String content;
        @JsonProperty("grammar_vocabulary") private String grammarVocabulary;
        @JsonProperty("action_items") private List<String> actionItems;

        // 새로 추가된 피드백 필드들
        @JsonProperty("correction_items") private List<CorrectionItemDTO> correctionItems;
        @JsonProperty("off_topic_items") private List<String> offTopicItems;
        @JsonProperty("corrected_answer") private String correctedAnswer;
        @JsonProperty("recommended_answer") private String recommendedAnswer;
        @JsonProperty("next_strategy") private String nextStrategy;
    }

    // 교정 항목 DTO
    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CorrectionItemDTO {
        private String type;
        private String original;
        private String issue;
        private String explanation;
        private String suggested;
        private String severity;
    }

    // 단어별 발음 시퀀스 DTO
    @Getter @NoArgsConstructor @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpokenWordDTO {
        private Integer index;
        @JsonProperty("segment_index") private Integer segmentIndex;
        @JsonProperty("word_index") private Integer wordIndex;
        private String word;
        private Long offset;
        private Long duration;
        @JsonProperty("accuracy_score") private Double accuracyScore;
        @JsonProperty("pronunciation_score") private Double pronunciationScore;
        @JsonProperty("error_type") private String errorType;
    }
}