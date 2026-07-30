package web.tosunsaeng.domain.blog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import web.tosunsaeng.domain.blog.application.BlogPostService;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.SuccessStatus;

@Tag(name = "Blog Post API", description = "공개 블로그 게시글 읽기 및 검색 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/posts")
public class BlogPostRestController {

    private final BlogPostService blogPostService;

    @Operation(summary = "공개 게시글 목록 조회")
    @GetMapping
    public BaseResponse<BlogPostResponseDTO.PostPageResult> getPosts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return BaseResponse.onSuccess(
                SuccessStatus.BLOG_POST_LIST,
                blogPostService.getPublicPosts(page, size));
    }

    @Operation(summary = "게시글 제목 부분 검색")
    @GetMapping("/search")
    public BaseResponse<BlogPostResponseDTO.PostSearchResult> searchPosts(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return BaseResponse.onSuccess(
                SuccessStatus.BLOG_POST_SEARCH,
                blogPostService.searchPublicPosts(query, page, size));
    }

    @Operation(summary = "slug 기반 공개 게시글 상세 조회")
    @GetMapping("/{slug}")
    public BaseResponse<BlogPostResponseDTO.PostDetailResult> getPost(
            @PathVariable("slug") String slug) {
        return BaseResponse.onSuccess(
                SuccessStatus.BLOG_POST_DETAIL,
                blogPostService.getPublicPost(slug));
    }
}
