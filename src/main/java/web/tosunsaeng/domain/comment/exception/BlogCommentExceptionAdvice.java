package web.tosunsaeng.domain.comment.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import web.tosunsaeng.domain.comment.api.BlogCommentRestController;
import web.tosunsaeng.domain.comment.domain.enums.CommentRule;
import web.tosunsaeng.domain.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = BlogCommentRestController.class)
public class BlogCommentExceptionAdvice {

    @ExceptionHandler(CommentValidationException.class)
    public ResponseEntity<BaseResponse<BlogCommentResponseDTO.ValidationFailureResult>>
            handleCommentValidation(CommentValidationException exception) {
        BlogCommentResponseDTO.ValidationFailureResult result = toFailureResult(
                exception.getViolations());
        return ResponseEntity
                .status(exception.getCode().getReasonHttpStatus().getHttpStatus())
                .body(BaseResponse.onFailure(exception.getCode(), result));
    }

    @ExceptionHandler(CommentRateLimitException.class)
    public ResponseEntity<BaseResponse<BlogCommentResponseDTO.RateLimitFailureResult>>
            handleCommentRateLimit(CommentRateLimitException exception) {
        BlogCommentResponseDTO.RateLimitFailureResult result =
                BlogCommentResponseDTO.RateLimitFailureResult.builder()
                        .retryAfterSeconds(exception.getRetryAfterSeconds())
                        .limitScope(exception.getLimitScope().name())
                        .build();
        return ResponseEntity
                .status(exception.getCode().getReasonHttpStatus().getHttpStatus())
                .header(
                        HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.getRetryAfterSeconds()))
                .body(BaseResponse.onFailure(exception.getCode(), result));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<BlogCommentResponseDTO.ValidationFailureResult>>
            handleUnreadableCommentBody(HttpMessageNotReadableException exception) {
        CommentRule rule = CommentRule.COMMENT_PLAIN_TEXT_ONLY;
        CommentValidator.Violation violation = new CommentValidator.Violation(
                rule.getRuleNumber(),
                rule.getRuleCode(),
                rule.getDefaultMessage());
        BlogCommentResponseDTO.ValidationFailureResult result =
                toFailureResult(List.of(violation));
        return ResponseEntity.badRequest().body(
                BaseResponse.onFailure(ErrorStatus._COMMENT_VALIDATION_FAILED, result));
    }

    private BlogCommentResponseDTO.ValidationFailureResult toFailureResult(
            List<CommentValidator.Violation> violations) {
        return BlogCommentResponseDTO.ValidationFailureResult.builder()
                .violations(violations.stream()
                        .map(violation -> BlogCommentResponseDTO.ViolationResult.builder()
                                .ruleNumber(violation.ruleNumber())
                                .ruleCode(violation.ruleCode())
                                .message(violation.message())
                                .build())
                        .toList())
                .build();
    }
}
