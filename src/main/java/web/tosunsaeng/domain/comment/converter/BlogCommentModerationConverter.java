package web.tosunsaeng.domain.comment.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class BlogCommentModerationConverter {

    private final AvatarImageUrlResolver avatarImageUrlResolver;

    public BlogCommentModerationDTO.ModeratedCommentPageResult toPageResult(
            Page<BlogComment> page,
            Map<String, String> postSlugById) {
        return BlogCommentModerationDTO.ModeratedCommentPageResult.builder()
                .comments(page.getContent().stream()
                        .map(comment -> toResult(
                                comment,
                                postSlugById.get(comment.getPostId())))
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }

    public BlogCommentModerationDTO.ModeratedCommentResult toResult(
            BlogComment comment,
            String postSlug) {
        return BlogCommentModerationDTO.ModeratedCommentResult.builder()
                .commentId(comment.getId())
                .postId(comment.getPostId())
                .postSlug(postSlug)
                .nickname(comment.getNickname())
                .avatarImageUrl(avatarImageUrlResolver.resolve(comment.getAvatarImageKey()))
                .content(comment.getContent())
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .hiddenAt(comment.getHiddenAt())
                .hiddenReason(comment.getHiddenReason())
                .build();
    }

    public BlogCommentModerationDTO.ModerationTransitionResult toTransitionResult(
            BlogComment comment) {
        return BlogCommentModerationDTO.ModerationTransitionResult.builder()
                .id(comment.getId())
                .status(comment.getStatus())
                .hiddenReason(comment.getHiddenReason())
                .hiddenAt(comment.getHiddenAt())
                .build();
    }
}
