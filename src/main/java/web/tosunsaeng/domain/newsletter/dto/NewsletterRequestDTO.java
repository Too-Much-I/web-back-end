package web.tosunsaeng.domain.newsletter.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class NewsletterRequestDTO {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscribeRequest {
        private String email;
        private Boolean consent;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnsubscribeRequest {
        private String token;
    }
}
