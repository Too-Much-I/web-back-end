package web.tosunsaeng.domain.exams.converter;

import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ExamConverter {

    public static ExamResponseDTO.CreateSessionResult toCreateSessionResult(String examId, List<ExamResponseDTO.QuestionDTO> questions) {
        return ExamResponseDTO.CreateSessionResult.builder()
                .examId(examId)
                .questions(questions)
                .build();
    }

    public static ExamResponseDTO.QuestionDTO toQuestionDTO(Question question) {
        return ExamResponseDTO.QuestionDTO.builder()
                .part(question.getPart())
                .questionId(question.getQuestionId())
                .text(question.getText())
                .prepTimeSec(question.getPrepTimeSec())
                .speakTimeSec(question.getSpeakTimeSec())
                .build();
    }

    public static ExamResponseDTO.UploadUrlResult toUploadUrlResult(String uploadUrl, String fileKey, Integer expiresIn) {
        return ExamResponseDTO.UploadUrlResult.builder()
                .uploadUrl(uploadUrl)
                .fileKey(fileKey)
                .expiresIn(expiresIn)
                .build();
    }

    // (수정) ExamStatus를 매개변수로 받도록 변경
    public static ExamResponseDTO.SubmitResult toSubmitResult(ExamStatus status) {
        return ExamResponseDTO.SubmitResult.builder()
                .status(status)
                .build();
    }

    // (수정) ExamStatus를 매개변수로 받도록 변경
    public static ExamResponseDTO.StatusResult toStatusResult(String examId, ExamStatus status, Integer progress) {
        return ExamResponseDTO.StatusResult.builder()
                .examId(examId)
                .overallStatus(status)
                .progressPercent(progress)
                .build();
    }

    public static ExamResponseDTO.ScoreResult toScoreResult(ExamResult result) {
        if (result == null) return null;

        ExamResult.Metrics metrics = (result.getMetrics() != null) ? result.getMetrics() :
                ExamResult.Metrics.builder().pronunciation("0").fluency("0").grammar("0").vocabulary("0").topicRelevance("0").build();

        ExamResponseDTO.MetricsDTO metricsDTO = ExamResponseDTO.MetricsDTO.builder()
                .pronunciation(metrics.getPronunciation())
                .fluency(metrics.getFluency())
                .grammar(metrics.getGrammar())
                .vocabulary(metrics.getVocabulary())
                .topicRelevance(metrics.getTopicRelevance())
                .build();

        List<ExamResponseDTO.PartResultDTO> partResultDTOs = Optional.ofNullable(result.getPartResults())
                .orElse(Collections.emptyList())
                .stream()
                .map(part -> ExamResponseDTO.PartResultDTO.builder()
                        .part(part.getPart())
                        .questionId(part.getQuestionId())
                        .sttText(part.getSttText())
                        .deductionReason(part.getDeductionReason())
                        .etsRubric(part.getEtsRubric())
                        .feedback(part.getFeedback())
                        .build())
                .collect(Collectors.toList());

        return ExamResponseDTO.ScoreResult.builder()
                .examId(result.getExamId())
                .totalScore(result.getTotalScore())
                .metrics(metricsDTO)
                .partResults(partResultDTOs)
                .build();
    }

    public static ExamResult toExamResult(ExamRequestDTO.AiResultReq req) {
        ExamResult.Metrics metrics = null;
        if(req.getMetrics() != null) {
            metrics = ExamResult.Metrics.builder()
                    .pronunciation(req.getMetrics().getPronunciation())
                    .fluency(req.getMetrics().getFluency())
                    .grammar(req.getMetrics().getGrammar())
                    .vocabulary(req.getMetrics().getVocabulary())
                    .topicRelevance(req.getMetrics().getTopicRelevance())
                    .build();
        }

        List<ExamResult.PartResult> partResults = null;
        if (req.getPartResults() != null) {
            partResults = req.getPartResults().stream()
                    .map(part -> ExamResult.PartResult.builder()
                            .part(part.getPart())
                            .questionId(part.getQuestionId())
                            .sttText(part.getSttText())
                            .deductionReason(part.getDeductionReason())
                            .etsRubric(part.getEtsRubric())
                            .feedback(part.getFeedback())
                            .build())
                    .collect(Collectors.toList());
        }

        return ExamResult.builder()
                .examId(req.getExamId())
                .totalScore(req.getTotalScore())
                .feedback(req.getFeedback())
                .metrics(metrics)
                .partResults(partResults)
                .build();
    }
}