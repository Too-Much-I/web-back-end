package web.tosunsaeng.domain.exams.converter;

import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExamConverter {

    // 1. 최종 세션 생성 응답 DTO 변환
    public static ExamResponseDTO.CreateSessionResult toCreateSessionResult(String examId, List<ExamResponseDTO.QuestionDTO> questions) {
        return ExamResponseDTO.CreateSessionResult.builder()
                .examId(examId)
                .questions(questions)
                .build();
    }

    // 2. 문제 정보 DTO 변환 (현재는 PoC용 데이터를 받지만, 추후 매개변수를 Question Entity로 변경)
    public static ExamResponseDTO.QuestionDTO toQuestionDTO(Integer part, String questionId, String text, Integer prepTimeSec, Integer speakTimeSec) {
        return ExamResponseDTO.QuestionDTO.builder()
                .part(part)
                .questionId(questionId)
                .text(text)
                .prepTimeSec(prepTimeSec)
                .speakTimeSec(speakTimeSec)
                .build();
    }

    public static ExamResponseDTO.UploadUrlResult toUploadUrlResult(String uploadUrl, String fileKey, Integer expiresIn) {
        return ExamResponseDTO.UploadUrlResult.builder()
                .uploadUrl(uploadUrl)
                .fileKey(fileKey)
                .expiresIn(expiresIn)
                .build();
    }

    public static ExamResponseDTO.SubmitResult toSubmitResult(String status) {
        return ExamResponseDTO.SubmitResult.builder()
                .status(status)
                .build();
    }

    public static ExamResponseDTO.StatusResult toStatusResult(String examId, String status, Integer progress) {
        return ExamResponseDTO.StatusResult.builder()
                .examId(examId)
                .overallStatus(status)
                .progressPercent(progress)
                .build();
    }

    public static ExamResponseDTO.ScoreResult toScoreResult(String examId, String estimatedScore,
                                                            ExamResponseDTO.MetricsDTO metrics,
                                                            List<ExamResponseDTO.PartResultDTO> partResults) {
        return ExamResponseDTO.ScoreResult.builder()
                .examId(examId)
                .estimatedScore(estimatedScore)
                .metrics(metrics)
                .partResults(partResults)
                .build();
    }

    // Entity -> DTO 변환 추가 (질문)
    public static ExamResponseDTO.QuestionDTO toQuestionDTO(Question question) {
        return ExamResponseDTO.QuestionDTO.builder()
                .part(question.getPart())
                .questionId(question.getQuestionId())
                .text(question.getText())
                .prepTimeSec(question.getPrepTimeSec())
                .speakTimeSec(question.getSpeakTimeSec())
                .build();
    }

    // Entity -> DTO 변환 추가 (결과)
    public static ExamResponseDTO.ScoreResult toScoreResult(ExamResult result) {
        if (result == null) return null;

        // 1. Metrics가 null이면 0으로 구성된 DTO 생성
        // Metrics가 null일 경우 대비
        ExamResult.Metrics metrics = (result.getMetrics() != null) ? result.getMetrics() :
                ExamResult.Metrics.builder()
                        .pronunciation("0")
                        .fluency("0")
                        .grammar("0")
                        .vocabulary("0")
                        .topicRelevance("0")
                        .build();

        ExamResponseDTO.MetricsDTO metricsDTO = ExamResponseDTO.MetricsDTO.builder()
                .pronunciation(metrics.getPronunciation())
                .fluency(metrics.getFluency())
                .grammar(metrics.getGrammar())
                .vocabulary(metrics.getVocabulary())
                .topicRelevance(metrics.getTopicRelevance())
                .build();

        // 2. PartResults가 null이면 빈 리스트 처리
        List<ExamResponseDTO.PartResultDTO> partResultDTOs = Optional.ofNullable(result.getPartResults())
                .orElse(Collections.emptyList()) // import java.util.Collections; 필요
                .stream()
                .map(part -> ExamResponseDTO.PartResultDTO.builder()
                        .part(part.getPart())
                        .sttText(part.getSttText())
                        .deductionReason(part.getDeductionReason())
                        .etsRubric(part.getEtsRubric())
                        .feedback(part.getFeedback())
                        .build())
                .collect(Collectors.toList());

        return ExamResponseDTO.ScoreResult.builder()
                .examId(result.getExamId())
                .estimatedScore(result.getEstimatedScore())
                .metrics(metricsDTO)
                .partResults(partResultDTOs)
                .build();
    }
}