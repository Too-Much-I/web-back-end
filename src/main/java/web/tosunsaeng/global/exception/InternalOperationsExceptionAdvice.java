package web.tosunsaeng.global.exception;

import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import web.tosunsaeng.domain.comment.api.InternalCommentController;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.newsletter.api.InternalNewsletterController;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        InternalCommentController.class,
        InternalNewsletterController.class
})
public class InternalOperationsExceptionAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BlogCommentException.class)
    public ResponseEntity<BaseResponse<Object>> handleComment(
            BlogCommentException exception) {
        return failure(exception.getCode());
    }

    @ExceptionHandler(NewsletterException.class)
    public ResponseEntity<BaseResponse<Object>> handleNewsletter(
            NewsletterException exception) {
        return failure(exception.getCode());
    }

    @ExceptionHandler(NewsletterEmailSendException.class)
    public ResponseEntity<BaseResponse<Object>> handleProviderFailure(
            NewsletterEmailSendException exception) {
        return failure(ErrorStatus._NEWSLETTER_PROVIDER_FAILURE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleUnexpected(Exception exception) {
        return failure(ErrorStatus._INTERNAL_SERVER_ERROR);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return failureObject(ErrorStatus._BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return failureObject(ErrorStatus._BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return failureObject(ErrorStatus._BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        return failureObject(ErrorStatus._BAD_REQUEST);
    }

    private ResponseEntity<BaseResponse<Object>> failure(BaseErrorCode code) {
        return ResponseEntity
                .status(code.getReasonHttpStatus().getHttpStatus())
                .body(BaseResponse.onFailure(code, null));
    }

    private ResponseEntity<Object> failureObject(BaseErrorCode code) {
        return ResponseEntity
                .status(code.getReasonHttpStatus().getHttpStatus())
                .body(BaseResponse.onFailure(code, null));
    }
}
