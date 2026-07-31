package web.tosunsaeng.domain.comment.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.comment.application.BlogCommentModerationService;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;
import web.tosunsaeng.domain.comment.dto.InternalCommentRequestDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

import java.time.Instant;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/comments")
public class InternalCommentController {

    private final BlogCommentModerationService moderationService;

    @GetMapping
    public BaseResponse<BlogCommentModerationDTO.ModeratedCommentPageResult> getComments(
            @RequestParam(value = "status", required = false) CommentStatus status,
            @RequestParam(value = "postId", required = false) String postId,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "createdAtFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant createdAtFrom,
            @RequestParam(value = "createdAtTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant createdAtTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        var filter = BlogCommentModerationDTO.CommentFilter.builder()
                .status(status)
                .postId(postId)
                .slug(slug)
                .createdAtFrom(createdAtFrom)
                .createdAtTo(createdAtTo)
                .page(page)
                .size(size)
                .build();
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_COMMENT_LIST,
                moderationService.getComments(filter));
    }

    @PatchMapping("/{commentId}/hide")
    public BaseResponse<BlogCommentModerationDTO.ModerationTransitionResult> hide(
            @PathVariable String commentId,
            @Valid @RequestBody InternalCommentRequestDTO.HideRequest request) {
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_COMMENT_HIDDEN,
                moderationService.hide(
                        commentId,
                        request == null ? null : request.reason()));
    }

    @PatchMapping("/{commentId}/restore")
    public BaseResponse<BlogCommentModerationDTO.ModerationTransitionResult> restore(
            @PathVariable String commentId) {
        return BaseResponse.onSuccess(
                SuccessStatus.INTERNAL_COMMENT_RESTORED,
                moderationService.restore(commentId));
    }
}
