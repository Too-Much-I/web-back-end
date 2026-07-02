package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
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

    private final QuestionRepository questionRepository;
    private final ExamResultRepository examResultRepository;

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

    private String getQuestionAudioUrl(String examPaperId, String questionId) {
        String fileKey = String.format("questions/%s/%s.wav", examPaperId, questionId);
        return generatePresignedGetUrl(fileKey, 60); // 시험 출제용 (60분)
    }

    private String getDownloadUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);
        return generatePresignedGetUrl(fileKey, 5); // 결과 확인용 (5분)
    }

    // --- 2. 유틸리티 로직: 문제 번호로 토스 파트 번호 계산 ---
    private String getPartNumber(String questionId) {
        try {
            // "q_001", "q1", "1" 등에서 숫자만 추출
            String numStr = questionId.replaceAll("[^0-9]", "");
            if (numStr.isEmpty()) return "1"; // 기본값 안전장치

            int qNum = Integer.parseInt(numStr);
            if (qNum >= 1 && qNum <= 2) return "1";
            if (qNum >= 3 && qNum <= 4) return "2";
            if (qNum >= 5 && qNum <= 7) return "3";
            if (qNum >= 8 && qNum <= 10) return "4";
            if (qNum == 11) return "5";
        } catch (Exception e) {
            log.warn("questionId에서 파트 추출 실패, 기본값 1 할당: {}", questionId);
        }
        return "1";
    }

    // --- 3. 비즈니스 로직 ---
    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        String redisKey = "exam:status:" + examId;
        // (수정) Enum 사용
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        String targetPaperId = "paper_001";
        List<Question> questions = questionRepository.findByExamPaperId(targetPaperId);

        if (questions.isEmpty()) {
            log.warn("MongoDB에 '{}'에 해당하는 문제가 없습니다.", targetPaperId);
        }

        List<ExamResponseDTO.QuestionDTO> questionDTOs = questions.stream()
                .map(question -> {
                    ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(question);
                    String audioUrl = getQuestionAudioUrl(targetPaperId, dto.getQuestionId());
                    dto.setAudioUrl(audioUrl);
                    return dto;
                })
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, questionDTOs);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

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
    public ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, MultipartFile audioFile) {
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PROCESSING.name(), 1, TimeUnit.HOURS);

        // [수정됨] 파일과 문자열을 섞어 보내기 위해 <String, Object>로 타입 변경
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("user_id", examId);
        body.add("mock_exam_id", "mock_001");
        body.add("part_number", getPartNumber(questionId));

        String qNumStr = questionId.replaceAll("[^0-9]", "");
        if (qNumStr.isEmpty()) qNumStr = "1";
        body.add("question_number", qNumStr);

        // [수정됨] MultipartFile의 원본 리소스를 추출하여 AI 서버에 그대로 전달
        body.add("audio_file", audioFile.getResource());

        HttpHeaders headers = new HttpHeaders();
        // RestTemplate이 알아서 boundary를 설정하도록 MediaType만 지정
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 에이전트 채점 요청 성공: {}", response.getBody());
        } catch (Exception e) {
            log.error("AI 에이전트 연동 실패: {}", e.getMessage());
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

    @Override
    public ExamResponseDTO.ScoreResult getExamResults(String examId) {
        ExamResult result = examResultRepository.findByExamId(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        ExamResponseDTO.ScoreResult scoreResult = ExamConverter.toScoreResult(result);

        if (scoreResult.getPartResults() != null) {
            scoreResult.getPartResults().forEach(partDto -> {
                if (partDto.getQuestionId() != null) {
                    String audioUrl = getDownloadUrl(examId, partDto.getQuestionId());
                    partDto.setAudioUrl(audioUrl);
                }
            });
        }

        return scoreResult;
    }

    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String redisKey = "exam:status:" + req.getExamId();
        // (수정) Enum 사용
        redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);

        ExamResult result = ExamConverter.toExamResult(req);
        examResultRepository.save(result);

        log.info("채점 완료 및 결과 저장 성공: {}", req.getExamId());
    }
}