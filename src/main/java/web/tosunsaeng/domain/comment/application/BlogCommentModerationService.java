package web.tosunsaeng.domain.comment.application;

import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;

public interface BlogCommentModerationService {

    BlogCommentModerationDTO.ModeratedCommentPageResult getComments(
            BlogCommentModerationDTO.CommentFilter filter);

    BlogCommentModerationDTO.ModeratedCommentResult hide(
            String commentId,
            HiddenReason reason);

    BlogCommentModerationDTO.ModeratedCommentResult restore(String commentId);
}
