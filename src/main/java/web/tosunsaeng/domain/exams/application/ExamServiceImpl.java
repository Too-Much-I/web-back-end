package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.*;
import web.tosunsaeng.domain.exams.domain.enums.ExamStatus;
import web.tosunsaeng.domain.exams.domain.repository.AzureResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.MockExamRepository;
import web.tosunsaeng.domain.exams.domain.repository.SpeechAceResultRepository;
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
import java.util.Map;
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
    private final String AI_SERVER_URL = "http://ai-server:8000/evaluations";

    private final ExamResultRepository examResultRepository;
    private final MockExamRepository mockExamRepository;
    private final SpeechAceResultRepository speechAceResultRepository;
    private final AzureResultRepository azureResultRepository;

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

    private String getQuestionGuideAudioUrl(String examPaperId) {
        String fileKey = String.format("questions/%s/part3_intro.wav", examPaperId);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 🌟 [수정] 업로드된 오디오 파일 경로가 재시도별로 유니크하도록 _r{retryCount} 추가 완
    private String getDownloadUrl(String examId, Integer questionNumber, Integer retryCount) {
        String fileKey = String.format("temp/%s/q_%d_r%d.wav", examId, questionNumber, retryCount);
        return generatePresignedGetUrl(fileKey, 5);
    }

    // --- 2. 유틸리티 로직: 문제 번호로 토스 파트 번호 계산 ---
    private Integer getPartNumber(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber == 0) return 0;
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

        // 🌟 mock_exam_003 족보 싱크 일치
        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        List<ExamResponseDTO.QuestionDTO> questionDTOs = mockExam.getQuestions().stream()
                .map(q -> {
                    ExamResponseDTO.QuestionDTO dto = ExamConverter.toQuestionDTO(q);
                    dto.setAudioUrl(getQuestionAudioUrl(mockExam.getMockExamId(), q.getQuestionNumber()));
                    if (q.getPartNumber() == 3) {
                        dto.setGuideAudioUrl(getQuestionGuideAudioUrl(mockExam.getMockExamId()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, mockExam.getTitle(), questionDTOs);
    }

    // 🌟 [수정] 오디오 파일 업로드용 Presigned URL 발급 시 retryCount 수용하도록 파라미터 확장
    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber, Integer retryCount) {
        String fileKey = String.format("temp/%s/q_%d_r%d.wav", examId, questionNumber, retryCount);

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

        return ExamConverter.toUploadUrlResult(url, fileKey, 60);
    }

    // 🌟 [수정] 오디오 파일 제출 시 retryCount를 멀티파트 폼 데이터에 정상 주입
    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber, Integer retryCount) {
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PROCESSING.name(), 1, TimeUnit.HOURS);

        try {
            String downloadUrl = getDownloadUrl(examId, questionNumber, retryCount);
            byte[] audioBytes = restTemplate.getForObject(java.net.URI.create(downloadUrl), byte[].class);

            if (audioBytes == null) {
                throw new RuntimeException("S3에서 오디오 파일을 읽어오지 못했습니다.");
            }

            org.springframework.core.io.ByteArrayResource audioResource = new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "q_" + questionNumber + "_r" + retryCount + ".webm";
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("user_id", examId);
            body.add("mock_exam_id", "mock_exam_003"); // 🌟 mock_exam_003 족보 매칭 완료
            body.add("part_number", getPartNumber(questionNumber));
            body.add("question_number", questionNumber);
            body.add("retry_count", retryCount);       // 🔥 AI 서버로 현재 몇 회차 채점 시도인지 전송!
            body.add("audio_file", audioResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 서버에 채점 요청 전송 완료: examId={}, qNum={}, retryCount={}", examId, questionNumber, retryCount);

        } catch (Exception e) {
            log.error("AI 서버 채점 요청 실패", e);
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
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String redisKey = "exam:status:" + req.getExamId();

        // 1. [기존 유지] AI 서버가 최종 종합 성적표(totalScore가 포함된 JSON)를 보내주면 정규 세션 종료 처리
        if (req.getTotalScore() != null) {
            redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
        }

        // 2. [기존 유지] AI 피드백 조각 몽고디비에 유니크하게 누적 적재 (retryCount 반영됨)
        ExamResult result = ExamConverter.toExamResult(req);
        examResultRepository.save(result);

        log.info("AI 피드백 조각 저장 완료: examId={}, qNum={}, retryCount={}",
                req.getExamId(), req.getQuestionNumber(), req.getRetryCount());

        // 🌟 3. 맛보기 세션 vs 정규 세션 종료 및 요약 트리거 분기 처리
        if (req.getQuestionNumber() != null) {
            String examId = req.getExamId();

            // [시나리오 A] 정규 모의고사(ex_)인 경우: 11번 문항이 끝났을 때 AI 서버에 최종 리포트 요약 생성을 비동기로 찌름
            if (examId.startsWith("ex_") && req.getQuestionNumber() == 11) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    requestOverallSummary(examId, req.getMockExamId());
                });
            }
            // [시나리오 B] 맛보기 체험(trial_)인 경우: 1번 문항 피드백이 들어오는 순간 즉시 세션을 COMPLETED 처리하고 리턴
            else if (examId.startsWith("trial_") && req.getQuestionNumber() == 1) {
                redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
                log.info("🏁 맛보기(Trial) 세션 최종 채점 완료 및 종료: examId={}", examId);
            }
        }
    }

    @Override
    public ExamResponseDTO.SummaryResult getExamSummary(String examId) {
        List<ExamResult> results = examResultRepository.findByExamId(examId);

        ExamResult summaryDoc = results.stream()
                .filter(r -> r.getTotalScore() != null)
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        // 파트별 '총합(Sum)' 계산 체계 유지
        java.util.Map<String, Double> partScores = results.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getScore() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> {
                            int partNum = r.getPartNumber() != null ? r.getPartNumber() : getPartNumber(r.getQuestionNumber());
                            return "part" + partNum;
                        },
                        java.util.stream.Collectors.summingDouble(ExamResult::getScore)
                ));

        partScores.replaceAll((part, sum) -> Math.round(sum * 10.0) / 10.0);

        return ExamConverter.toSummaryResult(summaryDoc, partScores);
    }

    // 🌟 [수정 및 전면 리팩토링 🔥] 성환님 DTO 스펙에 맞춘 중첩 구조(QuestionResult ➡️ PartResultDTO) 단건 핀포인트 조회
    @Override
    public ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber, Integer retryCount) {
        // 1. DB(MongoDB)에서 해당 시험 세션의 결과 조각 로드
        List<ExamResult> examResults = examResultRepository.findByExamId(examId);

        // 2. AzureResult는 레포지토리 정밀 쿼리로 단건 직접 조회
        AzureResult matchingAzure = azureResultRepository
                .findByExamIdAndQuestionNumberAndRetryCount(examId, questionNumber, retryCount)
                .orElse(null);

        Integer totalRetryCount = examResults.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getQuestionNumber().equals(questionNumber))
                .map(r -> r.getRetryCount() != null ? r.getRetryCount() : 0)
                .max(Integer::compare)
                .map(max -> max + 1) // 🌟 최댓값이 0이면 최초 도전을 포함해 총 '1회'가 되도록 +1 보정!
                .orElse(1);

        // 3. 문항 번호와 요청된 [retryCount] 회차가 동시에 일치하는 데이터 필터링
        ExamResult targetDoc = examResults.stream()
                .filter(r -> r.getQuestionNumber() != null
                        && r.getQuestionNumber().equals(questionNumber)
                        && (r.getRetryCount() != null ? r.getRetryCount() : 0) == retryCount)
                .findFirst()
                .orElse(null);

        // 4. mock_exam_003에서 원본 문제 공통 정보 조회
        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        Question rawQuestion = mockExam.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber().equals(questionNumber))
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._QUESTION_NOT_FOUND));

        // 🌟 5. 지저분한 빌더 패턴 조립을 '안 쓰이고 있던' ExamConverter에 통째로 위임!
        return ExamConverter.toQuestionResult(
                examId,
                questionNumber,
                retryCount,
                totalRetryCount,
                rawQuestion,
                targetDoc,
                matchingAzure,
                getDownloadUrl(examId, questionNumber, retryCount),
                getPartNumber(questionNumber)
        );
    }

    @Override
    public void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req) {
        SpeechAceResult result = SpeechAceResult.builder()
                .examId(req.getExamId())
                .questionNumber(req.getQuestionNumber())
                .retryCount(req.getRetryCount())
                .speechAceData(req.getSpeechAceData())
                .build();

        speechAceResultRepository.save(result);
        log.info("SpeechAce 전용 컬렉션 저장 완료: examId={}, questionNum={}", req.getExamId(), req.getQuestionNumber());
    }

    private void requestOverallSummary(String examId, String mockExamId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("user_id", examId);
            body.put("mock_exam_id", mockExamId != null ? mockExamId : "mock_exam_003"); // 🌟 mock_exam_003 싱크 완

            body.put("question_number", 0);
            body.put("part_number", 0);

            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 서버에 전체 요약 피드백 생성 요청 완료 (question_number=0): examId={}", examId);
        } catch (Exception e) {
            log.error("AI 서버 전체 요약 피드백 요청 실패: examId={}", examId, e);
        }
    }

    // 🌟 [수정] Azure 콜백 수신 메서드도 retry_count 세팅 누락 없도록 가로채기 보완 완료
    @Override
    @Transactional
    public void processAzureCallback(Map<String, Object> rawPayload) {
        Map<String, Object> metadata = (Map<String, Object>) rawPayload.get("metadata");
        String examId = (String) metadata.get("user_id");
        Integer questionNumber = (Integer) metadata.get("question_number");
        Integer retryCount = metadata.get("retry_count") != null ? (Integer) metadata.get("retry_count") : 0;

        log.info("🔥 Azure AI 서버 콜백 수신 (원본 통째로 저장): examId={}, questionNumber={}, retryCount={}", examId, questionNumber, retryCount);

        AzureResult entity = AzureResult.builder()
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount) // 🌟 매핑 주입
                .rawData(rawPayload)
                .build();

        azureResultRepository.save(entity);
    }

    // 인터페이스(ExamService)에도 꼭 메서드 시그니처를 추가해 주세요!
    @Override
    public ExamResponseDTO.CreateSessionResult createTrialSession() {
        // 🌟 1. 맛보기 전용 세션 ID 접두사 부여
        String examId = "trial_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String redisKey = "exam:status:" + examId;

        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("새로운 맛보기(Trial) 세션 생성 완료: {}", examId);

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 🌟 2. 1번 문제 딱 하나만 필터링해서 DTO로 변환
        List<ExamResponseDTO.QuestionDTO> trialQuestion = mockExam.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == 1) // 1번만 추출
                .map(ExamConverter::toQuestionDTO)
                .peek(dto -> dto.setAudioUrl(getQuestionAudioUrl(mockExam.getMockExamId(), dto.getQuestionNumber())))
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, mockExam.getTitle() + " (맛보기)", trialQuestion);
    }

    @Override
    @Transactional(readOnly = true)
    public ExamResponseDTO.QuestionPollResult getQuestionProcessingStatus(String examId, Integer questionNumber, Integer retryCount) {
        // 몽고디비 실물 안착 여부 탐색
        boolean isSaved = examResultRepository.existsByExamIdAndQuestionNumberAndRetryCount(examId, questionNumber, retryCount);

        // 있으면 COMPLETED, 없으면 AI 연산 중(PROCESSING)으로 간주
        ExamStatus questionStatus = isSaved ? ExamStatus.COMPLETED : ExamStatus.PROCESSING;

        return ExamResponseDTO.QuestionPollResult.builder()
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .status(questionStatus)
                .build();
    }
}