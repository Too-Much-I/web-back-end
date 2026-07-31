package web.tosunsaeng.domain.newsletter.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.newsletter.api.support.NewsletterClientIpResolver;
import web.tosunsaeng.domain.newsletter.application.NewsletterService;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Tag(name = "Newsletter API", description = "뉴스레터 구독과 구독 해지 API")
@RestController
@RequiredArgsConstructor
public class NewsletterRestController {

    private final NewsletterService newsletterService;
    private final NewsletterClientIpResolver clientIpResolver;

    @Operation(summary = "뉴스레터 구독")
    @PostMapping(
            value = "/api/newsletter/subscribe",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public BaseResponse<NewsletterResponseDTO.StatusResult> subscribe(
            @RequestBody NewsletterRequestDTO.SubscribeRequest request,
            HttpServletRequest servletRequest) {
        NewsletterResponseDTO.StatusResult result = newsletterService.subscribe(
                request,
                clientIpResolver.resolve(servletRequest));
        return BaseResponse.onSuccess(SuccessStatus.NEWSLETTER_SUBSCRIBED, result);
    }

    @Operation(summary = "뉴스레터 구독 해지")
    @PostMapping(
            value = "/api/newsletter/unsubscribe",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BaseResponse<NewsletterResponseDTO.StatusResult>> unsubscribe(
            @RequestBody NewsletterRequestDTO.UnsubscribeRequest request) {
        NewsletterResponseDTO.StatusResult result = newsletterService.unsubscribe(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(BaseResponse.onSuccess(
                        SuccessStatus.NEWSLETTER_UNSUBSCRIBED,
                        result));
    }
}
