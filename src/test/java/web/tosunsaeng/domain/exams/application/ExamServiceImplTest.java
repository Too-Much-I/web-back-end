package web.tosunsaeng.domain.exams.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExamServiceImplTest {

    private static final String EXAM_ID = "ex_tmi33";
    private static final String MOCK_EXAM_ID = "mock_exam_001";

    private RedisTemplate<String, Object> redisTemplate;
    private ValueOperations<String, Object> valueOperations;
    private S3Presigner s3Presigner;
    private RestTemplate restTemplate;
    private ExamResultRepository examResultRepository;
    private MockExamRepository mockExamRepository;
    private AzureResultRepository azureResultRepository;
    private ExamServiceImpl examService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        redisTemplate = mock(RedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        s3Presigner = mock(S3Presigner.class);
        restTemplate = mock(RestTemplate.class);
        examResultRepository = mock(ExamResultRepository.class);
        mockExamRepository = mock(MockExamRepository.class);
        azureResultRepository = mock(AzureResultRepository.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PresignedGetObjectRequest presignedGetObjectRequest = mock(PresignedGetObjectRequest.class);
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedGetObjectRequest);
        when(presignedGetObjectRequest.url()).thenReturn(URI.create("https://example.test/audio.wav").toURL());

        when(restTemplate.getForObject(any(URI.class), eq(byte[].class))).thenReturn(new byte[]{1});
        when(restTemplate.postForEntity(
                eq("http://ai-server:8000/evaluations"),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok("ok"));

        examService = new ExamServiceImpl(
                redisTemplate,
                s3Presigner,
                restTemplate,
                examResultRepository,
                mockExamRepository,
                mock(SpeechAceResultRepository.class),
                azureResultRepository
        );
        ReflectionTestUtils.setField(examService, "bucketName", "test-bucket");
    }

    @Test
    void createsRegularSessionFromMockExamOne() {
        MockExam mockExam = mockExamWith(List.of());
        when(mockExamRepository.findByMockExamId(MOCK_EXAM_ID)).thenReturn(Optional.of(mockExam));

        ExamResponseDTO.CreateSessionResult result = examService.createExamSession();

        verify(mockExamRepository).findByMockExamId(MOCK_EXAM_ID);
        verify(mockExamRepository, never()).findByMockExamId("mock_exam_003");
        assertThat(result.getTitle()).isEqualTo("4번 모의고사");
        assertThat(result.getQuestions()).isEmpty();
    }

    @Test
    void createsTrialSessionFromMockExamOne() {
        Question question = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("Question 1")
                .build();
        when(mockExamRepository.findByMockExamId(MOCK_EXAM_ID))
                .thenReturn(Optional.of(mockExamWith(List.of(question))));

        ExamResponseDTO.CreateSessionResult result = examService.createTrialSession();

        verify(mockExamRepository).findByMockExamId(MOCK_EXAM_ID);
        verify(mockExamRepository, never()).findByMockExamId("mock_exam_003");
        assertThat(result.getTitle()).isEqualTo("4번 모의고사 (맛보기)");
        assertThat(result.getQuestions()).extracting(ExamResponseDTO.QuestionDTO::getQuestionNumber)
                .containsExactly(1);
    }

    @Test
    void loadsQuestionDetailsFromMockExamOne() {
        Question question = Question.builder()
                .partNumber(1)
                .questionNumber(1)
                .question("Question 1")
                .build();
        when(examResultRepository.findByExamId(EXAM_ID)).thenReturn(List.of());
        when(azureResultRepository.findFirstByExamIdAndQuestionNumberAndRetryCountOrderByIdDesc(EXAM_ID, 1, 0))
                .thenReturn(Optional.empty());
        when(mockExamRepository.findByMockExamId(MOCK_EXAM_ID))
                .thenReturn(Optional.of(mockExamWith(List.of(question))));

        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(EXAM_ID, 1, 0);

        verify(mockExamRepository).findByMockExamId(MOCK_EXAM_ID);
        verify(mockExamRepository, never()).findByMockExamId("mock_exam_003");
        assertThat(result.getExamId()).isEqualTo(EXAM_ID);
        assertThat(result.getQuestion().getQuestionInfo().getText()).isEqualTo("Question 1");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sendsMockExamOneToAiForQuestionScoring() {
        examService.submitAudio(EXAM_ID, 1, 0);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://ai-server:8000/evaluations"),
                requestCaptor.capture(),
                eq(String.class)
        );

        MultiValueMap<String, Object> body = (MultiValueMap<String, Object>) requestCaptor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.getFirst("mock_exam_id")).isEqualTo(MOCK_EXAM_ID);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void usesMockExamOneWhenOverallSummaryRequestHasNoMockExamId() {
        ReflectionTestUtils.invokeMethod(examService, "requestOverallSummary", EXAM_ID, null);

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(
                eq("http://ai-server:8000/evaluations"),
                requestCaptor.capture(),
                eq(String.class)
        );

        Map<String, Object> body = (Map<String, Object>) requestCaptor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mock_exam_id")).isEqualTo(MOCK_EXAM_ID);
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

    private MockExam mockExamWith(List<Question> questions) {
        return MockExam.builder()
                .mockExamId(MOCK_EXAM_ID)
                .title("4번 모의고사")
                .questions(questions)
                .build();
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
        payload.put("mock_exam_id", MOCK_EXAM_ID);
        payload.put("part_number", 3);
        payload.put("question_number", questionNumber);
        payload.put("retry_count", retryCount);
        payload.put("score", score);

        return new ObjectMapper().convertValue(payload, ExamRequestDTO.AiResultReq.class);
    }
}
