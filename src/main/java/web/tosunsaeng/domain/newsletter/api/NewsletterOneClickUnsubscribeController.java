package web.tosunsaeng.domain.newsletter.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.newsletter.application.NewsletterService;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterLinkBuilder;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

@RestController
@RequiredArgsConstructor
public class NewsletterOneClickUnsubscribeController {

    private final NewsletterService newsletterService;

    @PostMapping(
            value = "/api/newsletter/one-click-unsubscribe/{token}",
            consumes = {
                    MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.MULTIPART_FORM_DATA_VALUE
            })
    public ResponseEntity<Void> unsubscribe(
            @PathVariable String token,
            @RequestParam(name = "List-Unsubscribe", required = false) String value) {
        if (!NewsletterLinkBuilder.ONE_CLICK_FORM_FIELD_VALUE.equals(value)) {
            throw new NewsletterException(
                    ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID);
        }
        newsletterService.unsubscribe(new NewsletterRequestDTO.UnsubscribeRequest(token));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
