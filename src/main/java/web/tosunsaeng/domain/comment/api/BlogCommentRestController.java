package web.tosunsaeng.domain.comment.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.comment.api.support.AnonymousCookieFactory;
import web.tosunsaeng.domain.comment.api.support.ClientIpResolver;
import web.tosunsaeng.domain.comment.application.BlogCommentService;
import web.tosunsaeng.domain.comment.dto.BlogCommentRequestDTO;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Tag(name = "Blog Comment API", description = "공개 게시글의 익명 댓글 조회와 작성 API")
@RestController
@RequiredArgsConstructor
public class BlogCommentRestController {

    private final BlogCommentService blogCommentService;
    private final AnonymousCookieFactory anonymousCookieFactory;
    private final ClientIpResolver clientIpResolver;

    @Operation(summary = "공개 게시글 댓글 목록 조회")
    @GetMapping("/api/posts/{slug}/comments")
    public BaseResponse<BlogCommentResponseDTO.CommentPageResult> getComments(
            @PathVariable("slug") String slug,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return BaseResponse.onSuccess(
                SuccessStatus.BLOG_COMMENT_LIST,
                blogCommentService.getComments(slug, page, size));
    }

    @Operation(summary = "공개 게시글 익명 댓글 작성")
    @PostMapping("/api/posts/{slug}/comments")
    public ResponseEntity<BaseResponse<BlogCommentResponseDTO.CreatedCommentResult>>
            createComment(
                    @PathVariable("slug") String slug,
                    @RequestBody BlogCommentRequestDTO.CreateCommentRequest request,
                    @CookieValue(
                            value = AnonymousCookieFactory.COOKIE_NAME,
                            required = false)
                    String rawToken,
                    HttpServletRequest servletRequest) {
        BlogCommentService.CreatedCommentSession session =
                blogCommentService.createComment(
                        slug,
                        request,
                        rawToken,
                        () -> clientIpResolver.resolve(servletRequest));
        if (session.acceptedWithoutCreation()) {
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(BaseResponse.onSuccess(
                            SuccessStatus.BLOG_COMMENT_REQUEST_ACCEPTED,
                            null));
        }
        return responseWithOptionalCookie(
                BaseResponse.onSuccess(SuccessStatus.BLOG_COMMENT_CREATED, session.result()),
                session.rawTokenToSet(),
                HttpStatus.CREATED);
    }

    @Operation(summary = "익명 닉네임과 아바타 재생성")
    @PostMapping("/api/comments/nickname/regenerate")
    public ResponseEntity<BaseResponse<BlogCommentResponseDTO.AnonymousProfileResult>>
            regenerateNickname(
                    @CookieValue(
                            value = AnonymousCookieFactory.COOKIE_NAME,
                            required = false)
                    String rawToken) {
        BlogCommentService.AnonymousProfileSession session =
                blogCommentService.regenerateAnonymousProfile(rawToken);
        return responseWithOptionalCookie(
                BaseResponse.onSuccess(
                        SuccessStatus.ANONYMOUS_PROFILE_REGENERATED,
                        session.result()),
                session.rawTokenToSet(),
                HttpStatus.OK);
    }

    private <T> ResponseEntity<BaseResponse<T>> responseWithOptionalCookie(
            BaseResponse<T> body,
            String rawTokenToSet,
            HttpStatus status) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (rawTokenToSet != null) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    anonymousCookieFactory.create(rawTokenToSet).toString());
        }
        return response.body(body);
    }
}
