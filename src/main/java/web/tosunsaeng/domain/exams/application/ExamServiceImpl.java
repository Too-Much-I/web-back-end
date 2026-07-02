package web.tosunsaeng.domain.exams.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import web.tosunsaeng.domain.exams.converter.ExamConverter;
import web.tosunsaeng.domain.exams.domain.entity.ExamResult;
import web.tosunsaeng.domain.exams.domain.entity.Question;
import web.tosunsaeng.domain.exams.domain.repository.ExamResultRepository;
import web.tosunsaeng.domain.exams.domain.repository.QuestionRepository;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.exams.exception.ExamsException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.net.URL;
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

    // MongoDB Repository 주입
    private final QuestionRepository questionRepository;
    private final ExamResultRepository examResultRepository;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Override
    public ExamResponseDTO.CreateSessionResult createExamSession() {
        // 1. 고유한 시험 세션 ID 생성
        String examId = "ex_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // 2. Redis에 세션 상태 저장 (키: "exam:status:{examId}", 값: "PENDING", 만료시간: 1시간)
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PENDING", 1, TimeUnit.HOURS);
        log.info("새로운 모의고사 세션 생성 완료: {}", examId);

        // 3. [DB 연동] 지정된 모의고사 세트의 문제 목록 조회
        // TODO: 향후 프론트엔드에서 파라미터로 넘겨받도록 고도화 가능
        String targetPaperId = "paper_001";
        List<Question> questions = questionRepository.findByExamPaperId(targetPaperId);

        if (questions.isEmpty()) {
            log.warn("MongoDB에 '{}'에 해당하는 문제가 없습니다. 데이터를 추가해 주세요.", targetPaperId);
        }

        // 4. Entity -> DTO 변환 (ExamConverter 사용)
        List<ExamResponseDTO.QuestionDTO> questionDTOs = questions.stream()
                .map(ExamConverter::toQuestionDTO)
                .collect(Collectors.toList());

        // 5. 최종 결과 조립 및 반환
        return ExamConverter.toCreateSessionResult(examId, questionDTOs);
    }

    @Override
    public ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, String questionId) {
        String fileKey = String.format("temp/%s/%s.wav", examId, questionId);

        // 1. S3에 PUT(업로드)할 객체 정보 생성
        software.amazon.awssdk.services.s3.model.PutObjectRequest objectRequest =
                software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(fileKey)
                        .build();

        // 2. 5분 동안 유효한 Presign 요청 생성
        software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest presignRequest =
                software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(objectRequest)
                        .build();

        // 3. 최종 URL 발급
        software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        String url = presignedRequest.url().toString();

        return ExamConverter.toUploadUrlResult(url, fileKey, 300);
    }

    @Override
    public ExamResponseDTO.SubmitResult submitAudio(String examId, String questionId, ExamRequestDTO.SubmitAudioReq req) {
        // 프론트엔드가 업로드를 마쳤다고 알림 -> 상태를 PROCESSING으로 변경
        String redisKey = "exam:status:" + examId;
        redisTemplate.opsForValue().set(redisKey, "PROCESSING", 1, TimeUnit.HOURS);

        // TODO: (고도화 시) 여기서 SQS 큐에 메시지를 보내 AI 에이전트를 비동기로 호출합니다.
        log.info("채점 요청 접수 완료 - Session: {}, Question: {}, FileKey: {}", examId, questionId, req.getFileKey());

        return ExamConverter.toSubmitResult("PROCESSING");
    }

    @Override
    public ExamResponseDTO.StatusResult getExamStatus(String examId) {
        String redisKey = "exam:status:" + examId;
        String status = (String) redisTemplate.opsForValue().get(redisKey);

        if (status == null) status = "FAILED"; // 세션 만료 등 예외 처리

        // PoC 테스트를 위해 임시로 진행도 60% 고정
        // (실제로는 AI 에이전트가 상태를 업데이트할 때마다 이 값을 읽어옴)
        return ExamConverter.toStatusResult(examId, status, 60);
    }

    @Override
    public ExamResponseDTO.ScoreResult getExamResults(String examId) {
        // [DB 연동] MongoDB에서 examId로 채점 결과를 조회합니다.
        ExamResult result = examResultRepository.findByExamId(examId)
                .orElseThrow(() -> new ExamsException(ErrorStatus._EXAM_NOT_FOUND));

        return ExamConverter.toScoreResult(result);
    }
}