package web.tosunsaeng.domain.exams.application;

import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

public interface ExamService {
    ExamResponseDTO.CreateSessionResult createExamSession();

    ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber);

    ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber, MultipartFile audioFile);

    ExamResponseDTO.StatusResult getExamStatus(String examId);

    ExamResponseDTO.ScoreResult getExamResults(String examId);

    void updateExamResult(ExamRequestDTO.AiResultReq req);
}