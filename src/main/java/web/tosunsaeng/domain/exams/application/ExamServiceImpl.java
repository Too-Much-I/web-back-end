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

    private final String AI_SERVER_URL = "http://ai-server:8000/evaluations";

    private final ExamResultRepository examResultRepository;
    private final MockExamRepository mockExamRepository;
    private final SpeechAceResultRepository speechAceResultRepository;
    private final AzureResultRepository azureResultRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    // --- 1. 유틸리티 메서드: S3 URL 생성 및 변환 ---

    // S3 객체 다운로드용 임시 Presigned URL을 발행합니다.
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

    // 문제 음성 파일의 S3 다운로드 주소를 획득합니다.
    private String getQuestionAudioUrl(String examPaperId, Integer questionNumber) {
        String fileKey = String.format("questions/%s/q_%d.wav", examPaperId, questionNumber);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 파트 3용 인트로 안내 음성 주소를 획득합니다.
    private String getQuestionGuideAudioUrl(String examPaperId) {
        String fileKey = String.format("questions/%s/part3_intro.wav", examPaperId);
        return generatePresignedGetUrl(fileKey, 60);
    }

    // 사용자가 제출한 오디오 파일 복원을 위한 임시 S3 Presigned URL을 획득합니다.
    private String getDownloadUrl(String examId, Integer questionNumber, Integer retryCount) {
        String fileKey = String.format("temp/%s/q_%d_r%d.wav", examId, questionNumber, retryCount);
        return generatePresignedGetUrl(fileKey, 5);
    }

    // --- 2. 유틸리티 메서드: 토익스피킹 파트 판별 ---

    // 문항 번호를 토대로 토익스피킹 파트(Part) 번호를 계산합니다.
    private Integer getPartNumber(Integer questionNumber) {
        if (questionNumber == null) return 1;
        if (questionNumber == 0) return 0;
        if (questionNumber >= 1 && questionNumber <= 2) return 1;
        if (questionNumber >= 3 && questionNumber <= 4) return 2;
        if (questionNumber >= 5 && questionNumber <= 7) return 3;
        if (questionNumber >= 8 && questionNumber <= 10) return 4;
        return 5;
    }

    // --- 3. 핵심 비즈니스 로직 구현체 ---

    // 새로운 정규 모의고사 세션을 생성하고 초기 시험 지문 및 S3 오디오 스트리밍 주소를 조립합니다.
    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String redisKey = "exam:status:" + examId;

        // Redis에 진행 상태를 대기(PENDING)로 등록하고 1시간 만료 시간을 부여합니다.
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("정규 모의고사 세션 생성 완료: {}", examId);

        // 지정된 족보 데이터인 mock_exam_003 셋을 MongoDB에서 로드합니다.
        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 전체 문항 배열을 순회하며 문항별 다운로드용 오디오 URL 및 가이드 URL을 결합합니다.
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

    // 사용자가 가상으로 녹음 오디오 파일을 업로드할 수 있는 임시 S3 PutObject용 Presigned URL을 발급합니다.
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

    // 사용자의 특정 문항 녹음 파일을 S3에서 바이트 배열로 읽어와 AI 채점 파이썬 서버로 멀티파트 요청을 토스합니다.
    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber, Integer retryCount) {
        String redisKey = "exam:status:" + examId;
        // 상태를 연산 중(PROCESSING)으로 변경하여 클라이언트의 폴링 진입을 유도합니다.
        redisTemplate.opsForValue().set(redisKey, ExamStatus.PROCESSING.name(), 1, TimeUnit.HOURS);

        try {
            String downloadUrl = getDownloadUrl(examId, questionNumber, retryCount);
            byte[] audioBytes = restTemplate.getForObject(java.net.URI.create(downloadUrl), byte[].class);

            if (audioBytes == null) {
                throw new RuntimeException("S3 Storage에서 오디오 소스를 데이터 배열로 로드하는 데 실패했습니다.");
            }

            org.springframework.core.io.ByteArrayResource audioResource = new org.springframework.core.io.ByteArrayResource(audioBytes) {
                @Override
                public String getFilename() {
                    return "q_" + questionNumber + "_r" + retryCount + ".webm";
                }
            };

            // AI 서버 전송용 파라미터 셋과 바이너리 리소스를 폼 데이터에 적재합니다.
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("user_id", examId);
            body.add("mock_exam_id", "mock_exam_003");
            body.add("part_number", getPartNumber(questionNumber));
            body.add("question_number", questionNumber);
            body.add("retry_count", retryCount);
            body.add("audio_file", audioResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 모델 채점 요청 발송 완료: examId={}, qNum={}, retryCount={}", examId, questionNumber, retryCount);

        } catch (Exception e) {
            log.error("AI 채점 서버 전송 오류 발생", e);
            redisTemplate.opsForValue().set(redisKey, ExamStatus.FAILED.name(), 1, TimeUnit.HOURS);
            throw new ExamsException(ErrorStatus._AI_SERVER_CONNECTION_ERROR);
        }

        return ExamConverter.toSubmitResult(ExamStatus.PROCESSING);
    }

    // 클라이언트가 결과 요약 팝업이나 대시보드 렌더링 시점에 호출하는 세션 전체 진행 상태를 빠른 메모리(Redis)에서 획득합니다.
    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String statusStr = (String) redisTemplate.opsForValue().get(redisKey);

        if (statusStr == null) statusStr = ExamStatus.FAILED.name();

        ExamStatus currentStatus = ExamStatus.valueOf(statusStr);
        return ExamConverter.toStatusResult(examId, currentStatus, 60);
    }

    // AI 서버 연산 완료 후 백엔드 웹훅 콜백을 통해 인입된 분석 스코어와 텍스트 피드백 데이터를 처리합니다.
    @Override
    public void updateExamResult(ExamRequestDTO.AiResultReq req) {
        String redisKey = "exam:status:" + req.getExamId();

        // AI 서버로부터 전체 최종 점수가 포함된 총합 데이터 수신 시 정규 세션 종료 처리
        if (req.getTotalScore() != null) {
            redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
        }

        // 수신된 개별 피드백 단건 조각을 MongoDB 레포지토리에 저장
        ExamResult result = ExamConverter.toExamResult(req);
        examResultRepository.save(result);

        log.info("AI 피드백 영구 데이터 적재 완료: examId={}, qNum={}, retryCount={}",
                req.getExamId(), req.getQuestionNumber(), req.getRetryCount());

        if (req.getQuestionNumber() != null) {
            String examId = req.getExamId();

            // 시나리오 A: 정규 시험 세션의 경우 마지막 11번 문항 콜백 수신 완료 후 전체 성적서 요약을 비동기 트리거
            if (examId.startsWith("ex_") && req.getQuestionNumber() == 11) {
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    requestOverallSummary(examId, req.getMockExamId());
                });
            }
            // 시나리오 B: 맛보기 시험 세션의 경우 1번 문항 단건 분석 결과 수신 즉시 완료 처리 후 복귀
            else if (examId.startsWith("trial_") && req.getQuestionNumber() == 1) {
                redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
                log.info("맛보기(Trial) 세션 최종 종료 완료: examId={}", examId);
            }
        }
    }

    // 특정 시험 세션의 AI 총합 진단 레코드와 파트별 획득 점수의 누적 가산 합산 값을 연산하여 성적표 리포트를 반환합니다.
    @Override
    public ExamResponseDTO.SummaryResult getExamSummary(String examId) {
        List<ExamResult> results = examResultRepository.findByExamId(examId);

        // 총점과 총평이 동시 적재된 종합 요약 문서(question_number=0)를 분리합니다.
        ExamResult summaryDoc = results.stream()
                .filter(r -> r.getTotalScore() != null)
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        // 파트별 세부 획득 점수의 누적 총합 연산
        java.util.Map<String, Double> partScores = results.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getScore() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        r -> {
                            int partNum = r.getPartNumber() != null ? r.getPartNumber() : getPartNumber(r.getQuestionNumber());
                            return "part" + partNum;
                        },
                        java.util.stream.Collectors.summingDouble(ExamResult::getScore)
                ));

        // 소수점 유실 방지 및 가독성을 위한 첫째 자리 반올림 정규화를 수행합니다.
        partScores.replaceAll((part, sum) -> Math.round(sum * 10.0) / 10.0);

        // 유저가 실제 풀이한 순수 문항 개수 산출 (retryCount == 0 이거나 null 체크, 종합요약 문서 제외)
        long totalSolvedQuestions = results.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getQuestionNumber() > 0)
                .filter(r -> r.getRetryCount() != null && r.getRetryCount() == 0)
                .count();

        return ExamConverter.toSummaryResult(summaryDoc, partScores, (int) totalSolvedQuestions);
    }

    // 유저가 채점 결과를 문항 단위로 핀포인트 조회할 때, 문제 원본(MongoDB)과 AI 결과 조각, Azure 발음 분석 세션을 결합합니다.
    @Override
    public ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber, Integer retryCount) {
        List<ExamResult> examResults = examResultRepository.findByExamId(examId);

        // Azure 연산 결과 레포지토리에서 문항 식별 및 특정 회차 타겟 레코드를 로드합니다.
        AzureResult matchingAzure = azureResultRepository
                .findByExamIdAndQuestionNumberAndRetryCount(examId, questionNumber, retryCount)
                .orElse(null);

        // 해당 문항에 대해 유저가 누적하여 도전한 총 횟수를 연산합니다.
        Integer totalRetryCount = examResults.stream()
                .filter(r -> r.getQuestionNumber() != null && r.getQuestionNumber().equals(questionNumber))
                .map(r -> r.getRetryCount() != null ? r.getRetryCount() : 0)
                .max(Integer::compare)
                .map(max -> max + 1)
                .orElse(1);

        // 현재 클라이언트가 요청한 특정 회차(retryCount)에 매칭되는 AI 채점 도큐먼트를 수색합니다.
        ExamResult targetDoc = examResults.stream()
                .filter(r -> r.getQuestionNumber() != null
                        && r.getQuestionNumber().equals(questionNumber)
                        && (r.getRetryCount() != null ? r.getRetryCount() : 0) == retryCount)
                .findFirst()
                .orElse(null);

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 모의고사 원본 데이터셋에서 현재 문항에 일치하는 기준 문제 엔티티를 검출합니다.
        Question rawQuestion = mockExam.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber().equals(questionNumber))
                .findFirst()
                .orElseThrow(() -> new ExamsException(ErrorStatus._QUESTION_NOT_FOUND));

        // 종합 응답 데이터 구조 결합 및 조립 처리를 전용 Converter 컴포넌트에 위임
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

    // 별도의 3rd 파티 발음 평가 데이터인 SpeechAce 분석의 원본 수록 JSON을 전용 가공 컬렉션에 영구 보존합니다.
    @Override
    public void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req) {
        SpeechAceResult result = SpeechAceResult.builder()
                .examId(req.getExamId())
                .questionNumber(req.getQuestionNumber())
                .retryCount(req.getRetryCount())
                .speechAceData(req.getSpeechAceData())
                .build();

        speechAceResultRepository.save(result);
        log.info("SpeechAce 가공 분석 원본 데이터 수복 완료: examId={}, questionNum={}", req.getExamId(), req.getQuestionNumber());
    }

    // AI 채점 서버에 종합 진단 및 전체 피드백 리포트(Summary) 생성을 비동기 요청합니다.
    private void requestOverallSummary(String examId, String mockExamId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 약속된 포맷인 0번 문항 플래그를 할당하여 AI 비동기 오케스트레이션을 수행합니다.
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("user_id", examId);
            body.put("mock_exam_id", mockExamId != null ? mockExamId : "mock_exam_003");

            body.put("question_number", 0);
            body.put("part_number", 0);

            HttpEntity<java.util.Map<String, Object>> entity = new HttpEntity<>(body, headers);

            restTemplate.postForEntity(AI_SERVER_URL, entity, String.class);
            log.info("AI 서버 종합 요약 피드백 생성 트리거 요청 완료: examId={}", examId);
        } catch (Exception e) {
            log.error("AI 서버 종합 요약 피드백 생성 트리거 요청 실패: examId={}", examId, e);
        }
    }

    // 외부 Azure 전용 음성 분석 모델로부터 수신한 대용량 JSON 페이로드를 매핑 변환 없이 물리 구조 그대로 누적 보존합니다.
    @Override
    @Transactional
    public void processAzureCallback(Map<String, Object> rawPayload) {
        Map<String, Object> metadata = (Map<String, Object>) rawPayload.get("metadata");
        String examId = (String) metadata.get("user_id");
        Integer questionNumber = (Integer) metadata.get("question_number");
        Integer retryCount = metadata.get("retry_count") != null ? (Integer) metadata.get("retry_count") : 0;

        log.info("Azure 음성인식 분석 원본 수신 및 저장 시작: examId={}, questionNumber={}, retryCount={}", examId, questionNumber, retryCount);

        AzureResult entity = AzureResult.builder()
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .rawData(rawPayload)
                .build();

        azureResultRepository.save(entity);
    }

    // 유저 유입 전환율 향상을 목적으로 1번 문항 단건만 전개하여 가볍게 연산하는 익스프레스 세션을 빌드합니다.
    @Override
    public ExamResponseDTO.CreateSessionResult createTrialSession() {
        String examId = "trial_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String redisKey = "exam:status:" + examId;

        redisTemplate.opsForValue().set(redisKey, ExamStatus.PENDING.name(), 1, TimeUnit.HOURS);
        log.info("맛보기(Trial) 모의고사 전용 임시 세션 생성 완료: {}", examId);

        MockExam mockExam = mockExamRepository.findByMockExamId("mock_exam_003")
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_PAPER_NOT_FOUND));

        // 맛보기 세션 운영 기준에 의거하여 1번 문항 데이터만 핀포인트 추출하여 DTO 매핑
        List<ExamResponseDTO.QuestionDTO> trialQuestion = mockExam.getQuestions().stream()
                .filter(q -> q.getQuestionNumber() != null && q.getQuestionNumber() == 1)
                .map(ExamConverter::toQuestionDTO)
                .peek(dto -> dto.setAudioUrl(getQuestionAudioUrl(mockExam.getMockExamId(), dto.getQuestionNumber())))
                .collect(Collectors.toList());

        return ExamConverter.toCreateSessionResult(examId, mockExam.getTitle() + " (맛보기)", trialQuestion);
    }

    // 프론트엔드가 개별 문항 녹음본을 제출한 후, 해당 단건 채점 분석 결과가 MongoDB에 도착했는지 추적하기 위한 폴링 엔드포인트용 조회 메서드입니다.
    @Override
    @Transactional(readOnly = true)
    public ExamResponseDTO.QuestionPollResult getQuestionProcessingStatus(String examId, Integer questionNumber, Integer retryCount) {
        boolean isSaved = examResultRepository.existsByExamIdAndQuestionNumberAndRetryCount(examId, questionNumber, retryCount);

        // MongoDB 피드백 엔티티 적재 완료 여부를 기준으로 문항별 채점 상태 분기 판별
        ExamStatus questionStatus = isSaved ? ExamStatus.COMPLETED : ExamStatus.PROCESSING;

        return ExamResponseDTO.QuestionPollResult.builder()
                .examId(examId)
                .questionNumber(questionNumber)
                .retryCount(retryCount)
                .status(questionStatus)
                .build();
    }

    // 사용자가 시험 화면에서 강제 종료 혹은 중단을 명시적으로 선택했을 때, 추가 오디오 제출을 완전 잠금 조치하고 AI 서버 종합 피드백 요약을 즉시 호출합니다.
    @Override
    public ExamResponseDTO.SubmitResult terminateAndRequestAiFeedback(String examId) {
        String redisKey = "exam:status:" + examId;

        String statusStr = (String) redisTemplate.opsForValue().get(redisKey);
        if (statusStr == null) {
            throw new ExamsException(ErrorStatus._EXAM_NOT_FOUND);
        }

        ExamStatus currentStatus = ExamStatus.valueOf(statusStr);
        if (currentStatus == ExamStatus.COMPLETED) {
            throw new ExamsException(ErrorStatus._EXAM_ALREADY_COMPLETED);
        }

        // 시험 중간 이탈 시의 레디스 상태 잠금 및 추가 제출 무효화 격리
        redisTemplate.opsForValue().set(redisKey, ExamStatus.COMPLETED.name(), 1, TimeUnit.HOURS);
        log.info("유저 명시적 요청에 의한 시험 세션 중도 종료 처리 완료: examId={}", examId);

        // 11번 완주 시점과 동일하게 CompletableFuture 풀을 통해 AI 비동기 요약 연산 트리거 호출
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            requestOverallSummary(examId, "mock_exam_003");
        });

        return ExamConverter.toSubmitResult(ExamStatus.COMPLETED);
    }
}