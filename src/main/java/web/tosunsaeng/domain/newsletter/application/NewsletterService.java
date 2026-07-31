package web.tosunsaeng.domain.newsletter.application;

import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;

public interface NewsletterService {

    NewsletterResponseDTO.StatusResult subscribe(
            NewsletterRequestDTO.SubscribeRequest request,
            String clientIp);

    NewsletterResponseDTO.StatusResult unsubscribe(
            NewsletterRequestDTO.UnsubscribeRequest request);
}
