package web.tosunsaeng.domain.exams.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import web.tosunsaeng.domain.exams.domain.entity.AzureResult;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExamConverter {

    public static ExamResponseDTO.CreateSessionResult toCreateSessionResult(String examId, String title, List<ExamResponseDTO.QuestionDTO> questions) {
        return ExamResponseDTO.CreateSessionResult.builder()
                .examId(examId)
                .title(title)
                .questions(questions)
                .build();
    }

    public static ExamResponseDTO.QuestionDTO toQuestionDTO(Question q) {
        return ExamResponseDTO.QuestionDTO.builder()
                .part(q.getPartNumber())
                .questionNumber(q.getQuestionNumber())
                .text(q.getQuestion())
                .referenceText(q.getReferenceText())
                .partIntroText(q.getPartIntroText())
                .imageUrl(q.getImageUrl())
                .tableContext(q.getTableContext())
                .audioUrl(q.getAudioUrl())
                .guideAudioUrl(q.getGuideAudioUrl())
                .prepTimeSec(q.getPrepTimeSec())
                .speakTimeSec(q.getSpeakTimeSec())
                .build();
    }

    public static ExamResponseDTO.UploadUrlResult toUploadUrlResult(String uploadUrl, String fileKey, Integer expiresIn) {
        return ExamResponseDTO.UploadUrlResult.builder()
                .uploadUrl(uploadUrl)
                .fileKey(fileKey)
                .expiresIn(expiresIn)
                .build();
    }

    public static ExamResponseDTO.SubmitResult toSubmitResult(ExamStatus status) {
        return ExamResponseDTO.SubmitResult.builder()
                .status(status)
                .build();
    }

    public static ExamResponseDTO.StatusResult toStatusResult(String examId, ExamStatus status, Integer progress) {
        return ExamResponseDTO.StatusResult.builder()
                .examId(examId)
                .overallStatus(status)
                .progressPercent(progress)
                .build();
    }

    // --- Req -> Entity 변환 ---
    public static ExamResult toExamResult(ExamRequestDTO.AiResultReq req) {
        if (req == null) return null;

        return ExamResult.builder()
                .examId(req.getExamId())
                .mockExamId(req.getMockExamId())
                .partNumber(req.getPartNumber())
                .questionNumber(req.getQuestionNumber())
                .retryCount(req.getRetryCount() != null ? req.getRetryCount() : 0) // 🌟 재시도 회차 누적 저장의 핵심!
                .score(req.getScore())
                .maxScore(req.getMaxScore())
                .transcript(req.getTranscript())
                .totalScore(req.getTotalScore())
                .levelEstimate(req.getLevelEstimate())
                .summary(req.getSummary())
                .overallFeedback(req.getOverallFeedback())
                .partFeedback(req.getPartFeedback())
                .strengths(req.getStrengths())
                .weaknesses(req.getWeaknesses())
                .recommendedPractice(req.getRecommendedPractice())
                // 기존 성환님 프로젝트 내부의 임베디드 매핑 메서드 명칭에 맞춰 연결되어 있습니다.
                .feedback(req.getFeedback() != null ? toItemFeedbackEntity(req.getFeedback()) : null)
                .spokenWordSequence(req.getSpokenWordSequence() != null ? toSpokenWordEntityList(req.getSpokenWordSequence()) : null)
                .build();
    }

    private static ExamResult.ItemFeedback toItemFeedbackEntity(ExamRequestDTO.ItemFeedbackDTO dto) {
        if (dto == null) return null;

        return ExamResult.ItemFeedback.builder()
                .summary(dto.getSummary())
                .level(dto.getLevel())
                .pronunciationFluencyScore(dto.getPronunciationFluencyScore())
                .contentRelevanceScore(dto.getContentRelevanceScore())
                .strengths(dto.getStrengths())
                .weaknesses(dto.getWeaknesses())
                .pronunciation(dto.getPronunciation())
                .fluency(dto.getFluency())
                .content(dto.getContent())
                .grammarVocabulary(dto.getGrammarVocabulary())
                .actionItems(dto.getActionItems())
                // 새로 추가된 필드들 매핑
                .correctionItems(toCorrectionItemEntityList(dto.getCorrectionItems()))
                .offTopicItems(dto.getOffTopicItems())
                .correctedAnswer(null)
                .recommendedAnswer(dto.getRecommendedAnswer())
                .nextStrategy(dto.getNextStrategy())
                .build();
    }

    // Entity <-> Req DTO 변환 헬퍼 메서드들
    private static List<ExamResult.SpokenWord> toSpokenWordEntityList(List<ExamRequestDTO.SpokenWordDTO> dtos) {
        if (dtos == null) return null;
        return dtos.stream().map(dto -> ExamResult.SpokenWord.builder()
                .index(dto.getIndex())
                .segmentIndex(dto.getSegmentIndex())
                .wordIndex(dto.getWordIndex())
                .word(dto.getWord())
                .offset(dto.getOffset())
                .duration(dto.getDuration())
                .accuracyScore(dto.getAccuracyScore())
                .pronunciationScore(dto.getPronunciationScore())
                .errorType(dto.getErrorType())
                .build()).collect(Collectors.toList());
    }

    private static List<ExamResult.CorrectionItem> toCorrectionItemEntityList(List<ExamRequestDTO.CorrectionItemDTO> dtos) {
        if (dtos == null) return null;
        return dtos.stream().map(dto -> ExamResult.CorrectionItem.builder()
                .type(dto.getType())
                .original(dto.getOriginal())
                .issue(dto.getIssue())
                .explanation(dto.getExplanation())
                .suggested(dto.getSuggested())
                .severity(dto.getSeverity())
                .build()).collect(Collectors.toList());
    }

    // --- Entity -> Res DTO 변환 ---
    public static ExamResponseDTO.ItemFeedbackDTO toItemFeedbackDTO(ExamResult.ItemFeedback entity) {
        if (entity == null) return null;

        return ExamResponseDTO.ItemFeedbackDTO.builder()
                .summary(entity.getSummary())
                .level(entity.getLevel())
                .pronunciationFluencyScore(entity.getPronunciationFluencyScore())
                .contentRelevanceScore(entity.getContentRelevanceScore())
                .strengths(entity.getStrengths())
                .weaknesses(entity.getWeaknesses())
                .pronunciation(entity.getPronunciation())
                .fluency(entity.getFluency())
                .content(entity.getContent())
                .grammarVocabulary(entity.getGrammarVocabulary())
                .actionItems(entity.getActionItems())
                // 새로 추가된 필드들 매핑
                .correctionItems(toCorrectionItemDTOList(entity.getCorrectionItems()))
                .offTopicItems(entity.getOffTopicItems())
                .correctedAnswer(entity.getCorrectedAnswer())
                .recommendedAnswer(entity.getRecommendedAnswer())
                .nextStrategy(entity.getNextStrategy())
                .build();
    }

    // Entity -> Res DTO 변환 헬퍼 메서드들
    public static List<ExamResponseDTO.SpokenWordDTO> toSpokenWordDTOList(List<ExamResult.SpokenWord> entities) {
        if (entities == null) return null;
        return entities.stream().map(entity -> ExamResponseDTO.SpokenWordDTO.builder()
                .index(entity.getIndex())
                .segmentIndex(entity.getSegmentIndex())
                .wordIndex(entity.getWordIndex())
                .word(entity.getWord())
                .offset(entity.getOffset())
                .duration(entity.getDuration())
                .accuracyScore(entity.getAccuracyScore())
                .pronunciationScore(entity.getPronunciationScore())
                .errorType(entity.getErrorType())
                .build()).collect(Collectors.toList());
    }

    private static List<ExamResponseDTO.CorrectionItemDTO> toCorrectionItemDTOList(List<ExamResult.CorrectionItem> entities) {
        if (entities == null) return null;
        return entities.stream().map(entity -> ExamResponseDTO.CorrectionItemDTO.builder()
                .type(entity.getType())
                .original(entity.getOriginal())
                .issue(entity.getIssue())
                .explanation(entity.getExplanation())
                .suggested(entity.getSuggested())
                .severity(entity.getSeverity())
                .build()).collect(Collectors.toList());
    }

    public static ExamResponseDTO.SummaryResult toSummaryResult(ExamResult summaryDoc, Map<String, Double> partScores) {
        if (summaryDoc == null) return null;
        return ExamResponseDTO.SummaryResult.builder()
                .examId(summaryDoc.getExamId())
                .totalScore(summaryDoc.getTotalScore())
                .levelEstimate(summaryDoc.getLevelEstimate())
                .summary(summaryDoc.getSummary())
                .overallFeedback(summaryDoc.getOverallFeedback())
                .partFeedback(summaryDoc.getPartFeedback())
                .strengths(summaryDoc.getStrengths())
                .weaknesses(summaryDoc.getWeaknesses())
                .recommendedPractice(summaryDoc.getRecommendedPractice())
                .partScores(partScores)
                .build();
    }

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ExamResponseDTO.AzureFeedbackDTO toAzureFeedbackDTO(AzureResult entity) {
        if (entity == null || entity.getRawData() == null) return null;
        Object speechResultObj = entity.getRawData().get("azure_speech_result");
        if (speechResultObj == null) return null;
        return objectMapper.convertValue(speechResultObj, ExamResponseDTO.AzureFeedbackDTO.class);
    }

    public static ExamResponseDTO.QuestionResult toQuestionResult(
            String examId,
            Integer questionNumber,
            Integer retryCount,
            Integer totalRetryCount,
            Question rawQuestion,
            ExamResult targetDoc,
            AzureResult matchingAzure,
            String downloadUrl,
            Integer calculatedPartNumber
    ) {
        ExamResponseDTO.QuestionDTO questionInfoDto = ExamConverter.toQuestionDTO(rawQuestion);

        ExamResponseDTO.ItemFeedbackDTO feedbackDto = (targetDoc != null && targetDoc.getFeedback() != null)
                ? toItemFeedbackDTO(targetDoc.getFeedback())
                : null;

        if (feedbackDto == null) {
            feedbackDto = ExamResponseDTO.ItemFeedbackDTO.builder().build();
        }

        if (rawQuestion != null) {
            feedbackDto.setCorrectedAnswer(rawQuestion.getCorrected_answer());
        }

        ExamResponseDTO.PartResultDTO partDto = ExamResponseDTO.PartResultDTO.builder()
                .questionInfo(questionInfoDto)
                .partNumber(targetDoc != null && targetDoc.getPartNumber() != null ? targetDoc.getPartNumber() : calculatedPartNumber)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .totalRetryCount(totalRetryCount)
                .audioUrl(downloadUrl)
                .score(targetDoc != null ? targetDoc.getScore() : null)
                .maxScore(targetDoc != null ? targetDoc.getMaxScore() : null)
                .transcript(targetDoc != null ? targetDoc.getTranscript() : null)
                .feedback(feedbackDto)

                .spokenWordSequence(targetDoc != null ? toSpokenWordDTOList(targetDoc.getSpokenWordSequence()) : null)
                .azureFeedback(matchingAzure != null ? toAzureFeedbackDTO(matchingAzure) : null)
                .build();

        return ExamResponseDTO.QuestionResult.builder()
                .examId(examId)
                .question(partDto)
                .build();
    }
}