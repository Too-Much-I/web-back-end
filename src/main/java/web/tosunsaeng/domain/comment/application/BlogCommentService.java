package web.tosunsaeng.domain.comment.application;

import web.tosunsaeng.domain.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;

import java.util.function.Supplier;

public interface BlogCommentService {

    BlogCommentResponseDTO.CommentPageResult getComments(String slug, int page, int size);

    CreatedCommentSession createComment(
            String slug,
            BlogCommentRequestDTO.CreateCommentRequest request,
            String rawToken,
            Supplier<String> clientIpSupplier);

    AnonymousProfileSession regenerateAnonymousProfile(String rawToken);

    record CreatedCommentSession(
            BlogCommentResponseDTO.CreatedCommentResult result,
            String rawTokenToSet,
            boolean acceptedWithoutCreation) {

        public CreatedCommentSession(
                BlogCommentResponseDTO.CreatedCommentResult result,
                String rawTokenToSet) {
            this(result, rawTokenToSet, false);
        }

        public static CreatedCommentSession acceptedRequest() {
            return new CreatedCommentSession(null, null, true);
        }
    }

    record AnonymousProfileSession(
            BlogCommentResponseDTO.AnonymousProfileResult result,
            String rawTokenToSet) {
    }
}
