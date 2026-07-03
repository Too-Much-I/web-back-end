package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.MockExam;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamServiceImpl implements ExamService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner;
    private final RestTemplate restTemplate;

    // AI 서버 주소
    private final String AI_SERVER_URL = "https://slighting-silent-dormitory.ngrok-free.dev/evaluations";

    private final ExamResultRepository examResultRepository;
    private final MockExamRepository mockExamRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // --- 1. 유틸리티 로직: S3 URL 통합 메서드 ---
    private String generatePresignedGetUrl(String fileKey, int expirationMinutes) {
        software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest =
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(expirationMinutes))
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private String getQuestionAudioUrl(String examPaperId, Integer questionNumber) {
        String fileKey = String.format("questions/%s/q_%d.wav", examPaperId, questionNumber);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 💡 버그 수정: getPresignedUrl의 "temp/%s/q_%d.wav" 파일 포맷과 정확히 일치시킴
    private String getDownloadUrl(String examId, Integer questionNumber) {
        String fileKey = String.format("temp/%s/q_%d.wav", examId, questionNumber);
        return generatePresignedGetUrl(fileKey, 5);
    }

    // --- 2. 유틸리티 로직: 문제 번호로 토스 파트 번호 계산 ---
    private Integer getPartNumber(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber >= 1 && questionNumber <= 2) return 1;
        if (questionNumber >= 3 && questionNumber <= 4) return 2;
        if (questionNumber >= 5 && questionNumber <= 7) return 3;
        if (questionNumber >= 8 && questionNumber <= 10) return 4;
        return 5;
    }

    // --- 3. 비즈니스 로직 ---
    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String redisKey = "exam:status:" + examId;

        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_001")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        List<ExamResponseDTO.QuestionDTO> questionDTOs = mockExam.getQuestions().stream()
                .map(q -> {
                    ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(q);
                    dto.setAudioUrl(getQuestionAudioUrl(mockExam.getMockExamId(), q.getQuestionNumber()));
                    return dto;
                })
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, mockExam.getTitle(), questionDTOs);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber) {
        String fileKey = String.format("temp/%s/q_%d.wav", examId, questionNumber);

        software.amazon.awssdk.services.s3.model.PutObjectRequest objectRequest =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(objectRequest)
                        .build();

        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        String url = presignedRequest.url().toString();

        return ExamConverter.toUploadUrlResult(url, fileKey, 300);
    }

    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber, MultipartFile audioFile) {
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PROCESSING.name(), 1, TimeUnit.HOURS);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", examId);
        body.add("mock_exam_id", "mock_001");
        body.add("part_number", getPartNumber(questionNumber));
        body.add("question_number", questionNumber);
        body.add("audio_file", audioFile.getResource());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
        } catch (Exception e) {
            redisTemplate.opsForValue().set(redisKey, ExamStatus.FAILED.name(), 1, TimeUnit.HOURS);
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        return ExamConverter.toSubmitResult(ExamStatus.PROCESSING);
    }

    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String statusStr = (String) redisTemplate.opsForValue().get(redisKey);

        if (statusStr == null) statusStr = ExamStatus.FAILED.name();

        ExamStatus currentStatus = ExamStatus.valueOf(statusStr);
        return ExamConverter.toStatusResult(examId, currentStatus, 60);
    }

    // 💡 1. AI 피드백 콜백 수신 및 저장 로직 구현 (누락 복구 완료)
    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String redisKey = "exam:status:" + req.getExamId();

        // 총점이 포함된 전체 요약본 JSON이 온 경우 세션을 최종 완료 처리
        if (req.getTotalScore() != null) {
            redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
        }

        // 유연한 NoSQL 구조를 활용해 피드백 조각을 무조건 신규 Document로 Insert
        ExamResult result = ExamConverter.toExamResult(req);
        examResultRepository.save(result);

        log.info("AI 피드백 조각 저장 완료: examId={}, isSummary={}", req.getExamId(), req.getTotalScore() != null);
    }

    @Override
    public ExamResponseDTO.SummaryResult getExamSummary(String examId) {
        List<ExamResult> results = examResultRepository.findByExamId(examId);

        // 요약 문서만 필터링
        ExamResult summaryDoc = results.stream()
                .filter(r -> r.getTotalScore() != null)
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

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
                .build();
    }

    @Override
    public ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber) {
        List<ExamResult> results = examResultRepository.findByExamId(examId);

        // 1. 해당 문제 번호 + AI 피드백이 들어있는 문서만 필터링 (SpeechAce 전용 문서는 무시됨)
        ExamResult targetDoc = results.stream()
                .filter(r -> r.getQuestionNumber() != null
                        && r.getQuestionNumber().equals(questionNumber)
                        && r.getFeedback() != null)
                .findFirst()
                .orElse(null);

        // 2. 기본 응답 뼈대 생성
        ExamResponseDTO.PartResultDTO partDto = ExamResponseDTO.PartResultDTO.builder()
                .partNumber(targetDoc != null && targetDoc.getPartNumber() != null ? targetDoc.getPartNumber() : getPartNumber(questionNumber))
                .questionNumber(questionNumber)
                .audioUrl(getDownloadUrl(examId, questionNumber))
                .build();

        // 3. AI 피드백 데이터 매핑 (SpeechAce 데이터는 프론트용 DTO에 없으므로 담지 않음)
        if (targetDoc != null) {
            partDto.setScore(targetDoc.getScore());
            partDto.setMaxScore(targetDoc.getMaxScore());
            partDto.setTranscript(targetDoc.getTranscript());
            partDto.setFeedback(ExamConverter.toItemFeedbackDTO(targetDoc.getFeedback()));
        }

        // 4. 단건 결과 반환
        return ExamResponseDTO.QuestionResult.builder()
                .examId(examId)
                .question(partDto)
                .build();
    }

    @Override
    public void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req) {
        ExamResult result = ExamResult.builder()
                .examId(req.getExamId())
                .questionNumber(req.getQuestionNumber())
                .speechAceData(req.getSpeechAceData())
                .build();

        examResultRepository.save(result);
        log.info("SpeechAce 조각 저장 완료: examId={}, questionNum={}", req.getExamId(), req.getQuestionNumber());
    }
}