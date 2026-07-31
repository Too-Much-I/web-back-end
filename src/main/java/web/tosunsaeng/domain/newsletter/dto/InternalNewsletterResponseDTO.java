package web.tosunsaeng.domain.newsletter.dto;

public final class InternalNewsletterResponseDTO {

    private InternalNewsletterResponseDTO() {
    }

    public record OperationResult(
            String postId,
            String status) {
    }

    public record RetryResult(
            String postId,
            int retriedCount,
            int skippedCount,
            boolean hasMore) {
    }
}
