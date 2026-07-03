package web.tosunsaeng.domain.exams.converter;

import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.List;

public class ExamConverter {

    // --- 1. 세션 생성 및 기본 문제 매핑 (기존 유지 코드) ---
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
                .text(q.getReferenceText() != null ? q.getReferenceText() : q.getQuestion())
                .audioUrl(q.getAudioUrl())
                .imageUrl(q.getImageUrl())
                .tableContext(q.getTableContext())
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

    // --- 2. 💡 추가된 신규 매핑 로직 (컴파일 에러 해결 및 API 분리 대응) ---

    // AI 결과(Req)를 MongoDB 엔티티(Entity) 문서 구조로 변환
    public static ExamResult toExamResult(ExamRequestDTO.AiResultReq req) {
        if (req == null) return null;

        return ExamResult.builder()
                .examId(req.getExamId())
                // 요약용 데이터 매핑 (문항 데이터일 때는 알아서 null로 채워짐)
                .totalScore(req.getTotalScore())
                .levelEstimate(req.getLevelEstimate())
                .summary(req.getSummary())
                .overallFeedback(req.getOverallFeedback())
                .partFeedback(req.getPartFeedback())
                .strengths(req.getStrengths())
                .weaknesses(req.getWeaknesses())
                .recommendedPractice(req.getRecommendedPractice())

                // 개별 문항용 데이터 매핑 (요약 데이터일 때는 알아서 null로 채워짐)
                .partNumber(req.getPartNumber())
                .questionNumber(req.getQuestionNumber())
                .score(req.getScore())
                .maxScore(req.getMaxScore())
                .transcript(req.getTranscript())
                .feedback(toItemFeedbackEntity(req.getFeedback()))
                .build();
    }

    // 엔티티의 하위 피드백 객체 매핑
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
                .build();
    }

    // ExamServiceImpl의 12개 조각 병합 과정 중 개별 피드백 DTO를 채워주기 위한 메서드
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
                .build();
    }

    public static ExamResponseDTO.SummaryResult toSummaryResult(ExamResult summaryDoc, java.util.Map<String, Double> partScores) {
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
                .partScores(partScores) // 파트별 점수 맵핑
                .build();
    }
}