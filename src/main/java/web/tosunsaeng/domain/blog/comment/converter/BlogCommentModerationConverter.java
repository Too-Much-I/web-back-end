package web.tosunsaeng.domain.blog.comment.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentModerationDTO;

@Component
@RequiredArgsConstructor
public class BlogCommentModerationConverter {

    private final AvatarImageUrlResolver avatarImageUrlResolver;

    public BlogCommentModerationDTO.ModeratedCommentPageResult toPageResult(
            Page<BlogComment> page) {
        return BlogCommentModerationDTO.ModeratedCommentPageResult.builder()
                .comments(page.getContent().stream().map(this::toResult).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }

    public BlogCommentModerationDTO.ModeratedCommentResult toResult(BlogComment comment) {
        return BlogCommentModerationDTO.ModeratedCommentResult.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .nickname(comment.getNickname())
                .avatarSeed(comment.getAvatarSeed())
                .avatarImageUrl(avatarImageUrlResolver.resolve(comment.getAvatarImageKey()))
                .content(comment.getContent())
                .status(comment.getStatus())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .hiddenAt(comment.getHiddenAt())
                .hiddenReason(comment.getHiddenReason())
                .build();
    }
}
