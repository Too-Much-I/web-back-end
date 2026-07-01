package web.tosunsaeng.domain.exams.converter;

import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.List;
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

        ExamResponseDTO.MetricsDTO metricsDTO = ExamResponseDTO.MetricsDTO.builder()
                .pronunciation(result.getMetrics().getPronunciation())
                .fluency(result.getMetrics().getFluency())
                .grammar(result.getMetrics().getGrammar())
                .vocabulary(result.getMetrics().getVocabulary())
                .topicRelevance(result.getMetrics().getTopicRelevance())
                .build();

        List<ExamResponseDTO.PartResultDTO> partResultDTOs = result.getPartResults().stream()
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