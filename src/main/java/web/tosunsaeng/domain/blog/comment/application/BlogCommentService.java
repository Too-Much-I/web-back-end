package web.tosunsaeng.domain.blog.comment.application;

import web.tosunsaeng.domain.blog.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentResponseDTO;

public interface BlogCommentService {

    BlogCommentResponseDTO.CommentPageResult getComments(String slug, int page, int size);

    CreatedCommentSession createComment(
            String slug,
            BlogCommentRequestDTO.CreateCommentRequest request,
            String rawToken);

    AnonymousProfileSession regenerateAnonymousProfile(String rawToken);

    record CreatedCommentSession(
            BlogCommentResponseDTO.CreatedCommentResult result,
            String rawTokenToSet) {
    }

    record AnonymousProfileSession(
            BlogCommentResponseDTO.AnonymousProfileResult result,
            String rawTokenToSet) {
    }
}
