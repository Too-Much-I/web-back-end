package web.tosunsaeng.domain.comment.dto;

import jakarta.validation.constraints.NotNull;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;

public final class InternalCommentRequestDTO {

    private InternalCommentRequestDTO() {
    }

    public record HideRequest(@NotNull HiddenReason reason) {
    }
}
