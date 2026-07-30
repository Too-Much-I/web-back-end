package web.tosunsaeng.domain.blog.comment.converter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.blog.comment.domain.entity.AnonymousVisitor;
import web.tosunsaeng.domain.blog.comment.domain.entity.BlogComment;
import web.tosunsaeng.domain.blog.comment.domain.policy.AvatarImageUrlResolver;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentResponseDTO;

@Component
@RequiredArgsConstructor
public class BlogCommentConverter {

    private final AvatarImageUrlResolver avatarImageUrlResolver;

    public BlogCommentResponseDTO.CommentPageResult toCommentPageResult(
            Page<BlogComment> page) {
        return BlogCommentResponseDTO.CommentPageResult.builder()
                .comments(page.getContent().stream()
                        .map(this::toCommentSummary)
                        .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .hasNext(page.hasNext())
                .build();
    }

    public BlogCommentResponseDTO.CreatedCommentResult toCreatedCommentResult(
            BlogComment comment) {
        return BlogCommentResponseDTO.CreatedCommentResult.builder()
                .id(comment.getId())
                .nickname(comment.getNickname())
                .avatarSeed(comment.getAvatarSeed())
                .avatarImageUrl(avatarImageUrlResolver.resolve(comment.getAvatarImageKey()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }

    public BlogCommentResponseDTO.AnonymousProfileResult toAnonymousProfileResult(
            AnonymousVisitor visitor) {
        return BlogCommentResponseDTO.AnonymousProfileResult.builder()
                .nickname(visitor.getNickname())
                .avatarSeed(visitor.getAvatarSeed())
                .avatarImageUrl(avatarImageUrlResolver.resolve(visitor.getAvatarImageKey()))
                .build();
    }

    private BlogCommentResponseDTO.CommentSummary toCommentSummary(BlogComment comment) {
        return BlogCommentResponseDTO.CommentSummary.builder()
                .id(comment.getId())
                .nickname(comment.getNickname())
                .avatarSeed(comment.getAvatarSeed())
                .avatarImageUrl(avatarImageUrlResolver.resolve(comment.getAvatarImageKey()))
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
