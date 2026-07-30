package web.tosunsaeng.domain.blog.comment.application;

import web.tosunsaeng.domain.blog.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentModerationDTO;

public interface BlogCommentModerationService {

    BlogCommentModerationDTO.ModeratedCommentPageResult getComments(
            BlogCommentModerationDTO.CommentFilter filter);

    BlogCommentModerationDTO.ModeratedCommentResult hide(
            String commentId,
            HiddenReason reason);

    BlogCommentModerationDTO.ModeratedCommentResult restore(String commentId);
}
