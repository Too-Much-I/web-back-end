package web.tosunsaeng.domain.exams.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Tag(name = "Exam API", description = "모의고사 세션 및 채점 관련 API")
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

    @Operation(summary = "S3 Presigned URL 발급 API", description = "녹음된 오디오를 S3에 직접 업로드하기 위한 일회성 주소를 발급합니다.")
    @GetMapping("/{examId}/questions/{questionId}/upload-url")
    public BaseResponse<ExamResponseDTO.UploadUrlResult> getUploadUrl(
            @PathVariable("examId") String examId,
            @PathVariable("questionId") Integer questionNumber) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getPresignedUrl(examId, questionNumber));
    }

    // [수정됨] JSON 대신 MultipartFile을 직접 입력받도록 변경
    @Operation(summary = "업로드 완료 알림 및 채점 요청 API (임시: 파일 직접 전송)", description = "S3 우회용으로 실제 음성 파일을 전송하여 AI 채점을 시작합니다.")
    @PostMapping(value = "/{examId}/questions/{questionId}/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<ExamResponseDTO.SubmitResult> submitAudio(
            @PathVariable("examId") String examId,
            @PathVariable("questionId") Integer questionNumber,
            @RequestPart("audio_file") MultipartFile audioFile) { // 실제 파일 수신
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.submitAudio(examId, questionNumber, audioFile));
    }

    @Operation(summary = "채점 진행 상태 조회 API", description = "비동기 채점이 완료되었는지 진행 상태를 폴링(Polling)합니다.")
    @GetMapping("/{examId}/status")
    public BaseResponse<ExamResponseDTO.StatusResult> getExamStatus(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamStatus(examId));
    }

    @Operation(summary = "AI 채점 결과 조회 API", description = "채점이 완료된 후, 예상 점수와 파트별 피드백을 가져옵니다.")
    @GetMapping("/{examId}/results")
    public BaseResponse<ExamResponseDTO.ScoreResult> getExamResults(
            @PathVariable("examId") String examId) {
        return BaseResponse.onSuccess(SuccessStatus.OK, examService.getExamResults(examId));
    }

    @Operation(summary = "AI 채점 완료 콜백 API", description = "채점이 완료된 후, AI 에이전트가 백엔드로 알립니다.")
    @PostMapping("/api/v1/exams/callback")
    public BaseResponse<Void> receiveAiResult(@RequestBody ExamRequestDTO.AiResultReq req) {
        examService.updateExamResult(req);
        return BaseResponse.onSuccess(SuccessStatus.OK, null);
    }
}