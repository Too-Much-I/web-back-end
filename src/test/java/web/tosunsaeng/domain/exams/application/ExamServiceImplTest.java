package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamServiceImplTest {

    private static final String EXAM_ID = "ex_tmi33";

    private ExamResultRepository examResultRepository;
    private ExamServiceImpl examService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        examResultRepository = mock(ExamResultRepository.class);
        examService = new ExamServiceImpl(
                mock(RedisTemplate.class),
                mock(S3Presigner.class),
                mock(RestTemplate.class),
                examResultRepository,
                mock(MockExamRepository.class),
                mock(SpeechAceResultRepository.class),
                mock(AzureResultRepository.class)
        );
    }

    @Test
    void returnsExistingPartScoresAndTotalScoreWhenOnlyInitialResultsExist() {
        givenResults(
                summaryResult(160),
                questionResult("q1", 1, 1, 0, 2.1),
                questionResult("q2", 1, 2, 0, 2.9)
        );

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertThat(result.getPartScores()).containsOnly(Map.entry("part1", 5.0));
        assertThat(result.getTotalSolvedQuestions()).isEqualTo(2);
        assertThat(result.getTotalScore()).isEqualTo(160);
    }

    @Test
    void excludesRetryScoresFromPartScores() {
        givenResults(
                summaryResult(150),
                questionResult("q5-r0", 3, 5, 0, 3.0),
                questionResult("q6-r0", 3, 6, 0, 3.0),
                questionResult("q7-r0", 3, 7, 0, 3.0),
                questionResult("q7-r1", 3, 7, 1, 0.8)
        );

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertThat(result.getPartScores()).containsOnly(Map.entry("part3", 9.0));
        assertThat(result.getTotalSolvedQuestions()).isEqualTo(3);
    }

    @Test
    void aggregatesOnlyInitialResultsAcrossMultipleParts() {
        givenResults(
                summaryResult(140),
                questionResult("q1-r0", 1, 1, 0, 2.0),
                questionResult("q5-r0", 3, 5, 0, 3.0),
                questionResult("q8-r0", 4, 8, 0, 2.5),
                questionResult("q8-r1", 4, 8, 1, 1.2)
        );

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertThat(result.getPartScores()).containsOnly(
                Map.entry("part1", 2.0),
                Map.entry("part3", 3.0),
                Map.entry("part4", 2.5)
        );
    }

    @Test
    void doesNotDoubleCountLegacyDuplicateCallbackDocuments() {
        givenResults(
                summaryResult(130),
                questionResult("legacy-duplicate-1", 3, 5, 0, 3.0),
                questionResult("legacy-duplicate-2", 3, 5, 0, 3.0)
        );

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertThat(result.getPartScores()).containsOnly(Map.entry("part3", 3.0));
        assertThat(result.getTotalSolvedQuestions()).isEqualTo(1);
    }

    @Test
    void countsOnlyDistinctInitialAttemptsAndTreatsLegacyNullRetryAsInitial() {
        givenResults(
                summaryResult(120),
                questionResult("q1-r0", 1, 1, 0, 2.0),
                questionResult("q1-r1", 1, 1, 1, 1.0),
                questionResult("q2-legacy", 1, 2, null, 2.5),
                questionResult("q3-r2", 2, 3, 2, 1.5)
        );

        ExamResponseDTO.SummaryResult result = examService.getExamSummary(EXAM_ID);

        assertThat(result.getPartScores()).containsOnly(Map.entry("part1", 4.5));
        assertThat(result.getTotalSolvedQuestions()).isEqualTo(2);
    }

    @Test
    void duplicateCallbackKeyIsSavedWithSameMongoId() {
        examService.updateExamResult(aiResultRequest(5, 0, 3.0));
        examService.updateExamResult(aiResultRequest(5, 0, 2.8));
        examService.updateExamResult(aiResultRequest(5, null, 3.0));
        examService.updateExamResult(aiResultRequest(5, 1, 0.8));

        ArgumentCaptor<ExamResult> savedResult = ArgumentCaptor.forClass(ExamResult.class);
        verify(examResultRepository, times(4)).save(savedResult.capture());

        List<ExamResult> savedResults = savedResult.getAllValues();
        ExamResult first = savedResults.get(0);
        ExamResult duplicate = savedResults.get(1);
        ExamResult nullRetry = savedResults.get(2);
        ExamResult retry = savedResults.get(3);

        assertThat(first.getId()).isNotNull();
        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(nullRetry.getId()).isEqualTo(first.getId());
        assertThat(nullRetry.getRetryCount()).isZero();
        assertThat(retry.getId()).isNotEqualTo(first.getId());
    }

    private void givenResults(ExamResult... results) {
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of(results));
    }

    private ExamResult summaryResult(int totalScore) {
        return ExamResult.builder()
                .id("summary")
                .examId(EXAM_ID)
                .partNumber(0)
                .questionNumber(0)
                .retryCount(0)
                .totalScore(totalScore)
                .build();
    }

    private ExamResult questionResult(
            String id,
            int partNumber,
            int questionNumber,
            Integer retryCount,
            double score
    ) {
        return ExamResult.builder()
                .id(id)
                .examId(EXAM_ID)
                .partNumber(partNumber)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .score(score)
                .build();
    }

    private ExamRequestDTO.AiResultReq aiResultRequest(int questionNumber, Integer retryCount, double score) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("user_id", EXAM_ID);
        payload.put("mock_exam_id", "mock_exam_003");
        payload.put("part_number", 3);
        payload.put("question_number", questionNumber);
        payload.put("retry_count", retryCount);
        payload.put("score", score);

        return new ObjectMapper().convertValue(payload, ExamRequestDTO.AiResultReq.class);
    }
}
