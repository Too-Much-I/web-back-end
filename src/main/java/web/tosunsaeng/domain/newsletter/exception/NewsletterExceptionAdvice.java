package web.tosunsaeng.domain.newsletter.exception;

import io.sentry.Sentry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import web.tosunsaeng.domain.newsletter.api.NewsletterRestController;
import web.tosunsaeng.domain.newsletter.api.NewsletterOneClickUnsubscribeController;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.BaseErrorCode;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        NewsletterRestController.class,
        NewsletterOneClickUnsubscribeController.class
})
public class NewsletterExceptionAdvice {

    private static final String UNSUBSCRIBE_PATH = "/api/newsletter/unsubscribe";
    private static final String ONE_CLICK_PREFIX =
            "/api/newsletter/one-click-unsubscribe/";

    @ExceptionHandler(NewsletterRateLimitException.class)
    public ResponseEntity<BaseResponse<NewsletterResponseDTO.RateLimitFailureResult>>
            handleRateLimit(NewsletterRateLimitException exception) {
        NewsletterResponseDTO.RateLimitFailureResult result =
                NewsletterResponseDTO.RateLimitFailureResult.builder()
                        .retryAfterSeconds(exception.getRetryAfterSeconds())
                        .build();
        return ResponseEntity
                .status(exception.getCode().getReasonHttpStatus().getHttpStatus())
                .header(
                        HttpHeaders.RETRY_AFTER,
                        Long.toString(exception.getRetryAfterSeconds()))
                .body(BaseResponse.onFailure(exception.getCode(), result));
    }

    @ExceptionHandler(NewsletterException.class)
    public ResponseEntity<BaseResponse<Object>> handleNewsletterException(
            NewsletterException exception,
            HttpServletRequest request) {
        return failure(exception.getCode(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Object>> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        BaseErrorCode code = isUnsubscribe(request)
                ? ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID
                : ErrorStatus._BAD_REQUEST;
        return failure(code, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        Sentry.captureException(exception);
        return failure(ErrorStatus._INTERNAL_SERVER_ERROR, request);
    }

    private ResponseEntity<BaseResponse<Object>> failure(
            BaseErrorCode code,
            HttpServletRequest request) {
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(code.getReasonHttpStatus().getHttpStatus());
        if (isUnsubscribe(request)) {
            builder.cacheControl(CacheControl.noStore());
        }
        return builder.body(BaseResponse.onFailure(code, null));
    }

    private boolean isUnsubscribe(HttpServletRequest request) {
        return request != null
                && (UNSUBSCRIBE_PATH.equals(request.getRequestURI())
                || request.getRequestURI().startsWith(ONE_CLICK_PREFIX));
    }
}
