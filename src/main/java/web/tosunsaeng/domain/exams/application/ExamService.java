package web.tosunsaeng.domain.exams.application;

import org.springframework.web.multipart.MultipartFile;
import web.tosunsaeng.domain.exams.dto.ExamRequestDTO;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;

public interface ExamService {
    ExamResponseDTO.CreateSessionResult createExamSession();

    ExamResponseDTO.UploadUrlResult getPresignedUrl(String examId, Integer questionNumber);

    ExamResponseDTO.SubmitResult submitAudio(String examId, Integer questionNumber);

    ExamResponseDTO.StatusResult getExamStatus(String examId);

    void updateExamResult(ExamRequestDTO.AiResultReq req);

    ExamResponseDTO.QuestionResult getExamQuestion(String examId, Integer questionNumber);

    void saveSpeechAceResult(ExamRequestDTO.SpeechAceReq req);

    ExamResponseDTO.SummaryResult getExamSummary(String examId);
}