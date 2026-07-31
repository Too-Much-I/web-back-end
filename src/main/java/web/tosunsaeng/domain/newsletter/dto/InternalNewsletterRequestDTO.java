package web.tosunsaeng.domain.newsletter.dto;

import jakarta.validation.constraints.NotBlank;

public final class InternalNewsletterRequestDTO {

    private InternalNewsletterRequestDTO() {
    }

    public record TestSendRequest(@NotBlank String email) {
    }
}
