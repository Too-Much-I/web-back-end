package web.tosunsaeng.domain.newsletter.converter;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterSubscriberStatus;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;

@Component
public class NewsletterConverter {

    public NewsletterResponseDTO.StatusResult toStatusResult(
            NewsletterSubscriberStatus status) {
        return NewsletterResponseDTO.StatusResult.builder()
                .status(status.name())
                .build();
    }
}
