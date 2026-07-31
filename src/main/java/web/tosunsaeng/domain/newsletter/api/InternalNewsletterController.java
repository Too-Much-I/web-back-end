package web.tosunsaeng.domain.newsletter.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.newsletter.application.NewsletterOperationsService;
import web.tosunsaeng.domain.newsletter.dto.InternalNewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.InternalNewsletterResponseDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/newsletter/posts/{postId}")
public class InternalNewsletterController {

    private final NewsletterOperationsService operationsService;

    @PostMapping("/test")
    public BaseResponse<InternalNewsletterResponseDTO.OperationResult> sendTest(
            @PathVariable String postId,
            @Valid @RequestBody InternalNewsletterRequestDTO.TestSendRequest request) {
        NewsletterOperationsService.TestSendResult result = operationsService.sendTest(
                postId,
                request == null ? null : request.email());
        if (result == NewsletterOperationsService.TestSendResult.DISABLED) {
            throw new NewsletterException(ErrorStatus._NEWSLETTER_TEST_SEND_DISABLED);
        }
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_NEWSLETTER_TEST_SENT,
                new InternalNewsletterResponseDTO.OperationResult(postId, "SENT"));
    }

    @PostMapping("/cancel")
    public BaseResponse<InternalNewsletterResponseDTO.OperationResult> cancel(
            @PathVariable String postId) {
        operationsService.cancelScheduledCampaignByPostId(postId);
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_NEWSLETTER_CANCELED,
                new InternalNewsletterResponseDTO.OperationResult(postId, "CANCELED"));
    }

    @PostMapping("/retry")
    public BaseResponse<InternalNewsletterResponseDTO.RetryResult> retry(
            @PathVariable String postId) {
        NewsletterOperationsService.ManualRetryBatchResult result =
                operationsService.retryFailedDeliveriesByPostId(postId);
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_NEWSLETTER_RETRY_REGISTERED,
                new InternalNewsletterResponseDTO.RetryResult(
                        postId,
                        result.retriedCount(),
                        result.skippedCount(),
                        result.hasMore()));
    }
}
