package web.tosunsaeng.domain.exams.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

import java.util.Map;

@Tag(name = "Exam API", description = "모의고사 세션 및 채점 관련 API")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/exams")
public class ExamRestController {

    private final ExamService examService;

    @Operation(summary = "모의고사 세션 생성 API", description = "체험 시작 시 새로운 세션을 발급하고 문제를 반환합니다.")
    @PostMapping("")
    public BaseResponse<ExamResponseDTO.CreateSessionResult> createSession() {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.createExamSession());
    }

    @Operation(summary = "S3 Presigned URL 발급 API", description = "녹음된 오디오를 S3에 직접 업로드하기 위한 회차별(retryCount) 고유 주소를 발급합니다.")
    @GetMapping("/{examId}/questions/{questionNumber}/upload-url")
    public BaseResponse<ExamResponseDTO.UploadUrlResult> getUploadUrl(
            @PathVariable("examId") String examId,
            @PathVariable("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount // 🌟 [수정] 회차 식별자 파라미터 추가
    ) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getPresignedUrl(examId, questionNumber, retryCount));
    }

    @Operation(summary = "업로드 완료 알림 및 채점 요청 API", description = "S3 우회용으로 실제 음성 파일을 전송하여 AI 채점을 시작합니다. 몇 번째 재시도인지(retryCount)를 함께 전달합니다.")
    @PostMapping(value = "/{examId}/questions/{questionNumber}/submit")
    public BaseResponse<ExamResponseDTO.SubmitResult> submitAudio(
            @PathVariable("examId") String examId,
            @PathVariable("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount // 🌟 [수정] 회차 식별자 파라미터 추가
    ) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.submitAudio(examId, questionNumber, retryCount));
    }

    @Operation(summary = "채점 진행 상태 조회 API", description = "비동기 채점이 완료되었는지 진행 상태를 폴링(Polling)합니다.")
    @GetMapping("/{examId}/status")
    public BaseResponse<ExamResponseDTO.StatusResult> getExamStatus(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamStatus(examId));
    }

    @Operation(summary = "[프론트엔드] 전체 요약 피드백 조회 API", description = "모의고사의 총점 및 요약 피드백만 빠르게 가져옵니다.")
    @GetMapping("/{examId}/summary")
    public BaseResponse<ExamResponseDTO.SummaryResult> getExamSummary(@PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamSummary(examId));
    }

    @Operation(summary = "문항별 채점 피드백 정밀 단건 조회 API", description = "특정 문항의 콕 집은 회차(retryCount) 피드백 내용과 공통 문제 정보를 조회합니다.")
    @GetMapping("/{examId}/questions")
    public BaseResponse<ExamResponseDTO.QuestionResult> getExamQuestion(
            @PathVariable String examId,
            @RequestParam Integer questionNumber,
            @RequestParam(defaultValue = "0") Integer retryCount
    ) {
        ExamResponseDTO.QuestionResult result = examService.getExamQuestion(examId, questionNumber, retryCount);
        return BaseResponse.onSuccess(SuccessStatus.OK, result);
    }

    @Operation(summary = "[AI 서버용] 채점 피드백 콜백 API", description = "AI가 분석한 결과를 부분적으로 저장합니다.")
    @PostMapping("/callback/feedback")
    public BaseResponse<Void> receiveAiResult(@RequestBody ExamRequestDTO.AiResultReq req) {
        log.info("☎️ [AI 콜백 수신] ExamID: {}, Part: {}, Question: {}, RetryCount: {}",
                req.getExamId(), req.getPartNumber(), req.getQuestionNumber(), req.getRetryCount());
        examService.updateExamResult(req);
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }

    @Operation(summary = "[AI 서버용] SpeechAce 결과 콜백 API", description = "AI가 호출한 스피치에이스 JSON을 저장합니다.")
    @PostMapping("/callback/speechace")
    public BaseResponse<Void> receiveSpeechAceResult(@RequestBody ExamRequestDTO.SpeechAceReq req) {
        examService.saveSpeechAceResult(req);
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }

    @Operation(summary = "[AI 서버용] azure 결과 콜백 API", description = "AI가 호출한 azure 원본 JSON을 통째로 저장합니다.")
    @PostMapping("/callback/azure")
    public BaseResponse<String> azureCallback(@RequestBody Map<String, Object> rawPayload) {
        examService.processAzureCallback(rawPayload);
        return BaseResponse.onSuccess(SuccessStatus.OK, "Azure 콜백 데이터 원본이 성공적으로 저장되었습니다.");
    }

    @Operation(summary = "맛보기(Trial) 세션 생성 API", description = "1번 문제만 풀어볼 수 있는 맛보기 세션을 발급합니다.")
    @PostMapping("/trial")
    public BaseResponse<ExamResponseDTO.CreateSessionResult> createTrialSession() {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.createTrialSession());
    }

    @Operation(summary = "문항별 재시도 채점 진행 상태 조회 (폴링) API", description = "특정 문항의 콕 집은 회차(retryCount) 채점이 완료되었는지 폴링합니다.")
    @GetMapping("/{examId}/questions/status")
    public BaseResponse<ExamResponseDTO.QuestionPollResult> getQuestionStatus(
            @PathVariable("examId") String examId,
            @RequestParam("questionNumber") Integer questionNumber,
            @RequestParam(value = "retryCount", defaultValue = "0") Integer retryCount
    ) {
        ExamResponseDTO.QuestionPollResult result = examService.getQuestionProcessingStatus(examId, questionNumber, retryCount);
        return BaseResponse.onSuccess(SuccessStatus.OK, result);
    }

    @PostMapping("/{examId}/terminate")
    public BaseResponse<ExamResponseDTO.SubmitResult> terminateExam(
            @PathVariable String examId
    ) {
        ExamResponseDTO.SubmitResult result = examService.terminateAndRequestAiFeedback(examId);
        return BaseResponse.onSuccess(SuccessStatus.OK, result);
    }
}