package web.tosunsaeng.domain.blog.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.domain.blog.application.BlogPostService;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;
import web.tosunsaeng.domain.blog.exception.BlogPostException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BlogPostRestControllerTest {

    private static final Instant PUBLISHED_AT = Instant.parse("2026-07-29T07:00:00Z");

    private BlogPostService blogPostService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        blogPostService = mock(BlogPostService.class);
        mockMvc = standaloneSetup(new BlogPostRestController(blogPostService))
                .setControllerAdvice(new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void returnsListSuccessBaseResponse() throws Exception {
        when(blogPostService.getPublicPosts(0, 10)).thenReturn(pageResult(10));

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("BLOG_200"))
                .andExpect(jsonPath("$.message").value("게시글 목록을 조회했습니다."))
                .andExpect(jsonPath("$.result.posts[0].slug").value("post-1"))
                .andExpect(jsonPath("$.result.posts[0].contentMarkdown").doesNotExist())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.hasNext").value(false));

        verify(blogPostService).getPublicPosts(0, 10);
    }

    @Test
    void returnsDetailSuccessBaseResponseWithoutInternalStatus() throws Exception {
        when(blogPostService.getPublicPost("post-1")).thenReturn(detailResult());

        mockMvc.perform(get("/api/posts/post-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("BLOG_201"))
                .andExpect(jsonPath("$.result.slug").value("post-1"))
                .andExpect(jsonPath("$.result.contentMarkdown").value("# 본문"))
                .andExpect(jsonPath("$.result.relatedPosts[0].slug").value("related-1"))
                .andExpect(jsonPath("$.result.viewCount").doesNotExist())
                .andExpect(jsonPath("$.result.status").doesNotExist())
                .andExpect(jsonPath("$.result.id").doesNotExist())
                .andExpect(jsonPath("$.result.relatedPostSlugs").doesNotExist());
    }

    @Test
    void returnsSearchSuccessBaseResponseWithNormalizedQuery() throws Exception {
        when(blogPostService.searchPublicPosts(" 토익스피킹 ", 0, 10))
                .thenReturn(searchResult());

        mockMvc.perform(get("/api/posts/search")
                        .param("q", " 토익스피킹 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("BLOG_202"))
                .andExpect(jsonPath("$.result.query").value("토익스피킹"))
                .andExpect(jsonPath("$.result.posts").isArray())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void returnsSameNotFoundBaseResponse() throws Exception {
        when(blogPostService.getPublicPost("missing"))
                .thenThrow(new BlogPostException(ErrorStatus._BLOG_POST_NOT_FOUND));

        mockMvc.perform(get("/api/posts/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("BLOG_4004"))
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsSearchLengthError() throws Exception {
        when(blogPostService.searchPublicPosts("가", 0, 10))
                .thenThrow(new BlogPostException(ErrorStatus._BLOG_SEARCH_QUERY_TOO_SHORT));

        mockMvc.perform(get("/api/posts/search").param("q", "가"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4001"));
    }

    @Test
    void returnsNegativePageError() throws Exception {
        when(blogPostService.getPublicPosts(-1, 10))
                .thenThrow(new BlogPostException(ErrorStatus._BLOG_PAGE_NEGATIVE));

        mockMvc.perform(get("/api/posts").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4003"));
    }

    @Test
    void returnsSizeBelowOneError() throws Exception {
        when(blogPostService.getPublicPosts(0, 0))
                .thenThrow(new BlogPostException(ErrorStatus._BLOG_SIZE_TOO_SMALL));

        mockMvc.perform(get("/api/posts").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4005"));
    }

    @Test
    void acceptsSizeOneHundred() throws Exception {
        when(blogPostService.getPublicPosts(0, 100)).thenReturn(pageResult(100));

        mockMvc.perform(get("/api/posts").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
    }

    @Test
    void returnsSizeAboveOneHundredError() throws Exception {
        when(blogPostService.getPublicPosts(0, 101))
                .thenThrow(new BlogPostException(ErrorStatus._BLOG_SIZE_TOO_LARGE));

        mockMvc.perform(get("/api/posts").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4006"));
    }

    @Test
    void doesNotExposePostCreateApi() throws Exception {
        assertNotRouted(post("/api/posts"));
        verifyNoInteractions(blogPostService);
    }

    @Test
    void doesNotExposePostUpdateApi() throws Exception {
        assertNotRouted(put("/api/posts/post-1"));
        verifyNoInteractions(blogPostService);
    }

    @Test
    void doesNotExposePostPatchApi() throws Exception {
        assertNotRouted(patch("/api/posts/post-1"));
        verifyNoInteractions(blogPostService);
    }

    @Test
    void doesNotExposePostDeleteApi() throws Exception {
        assertNotRouted(delete("/api/posts/post-1"));
        verifyNoInteractions(blogPostService);
    }

    @Test
    void doesNotAddCommentApi() throws Exception {
        mockMvc.perform(get("/api/comments"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(blogPostService);
    }

    @Test
    void doesNotAddNewsletterApi() throws Exception {
        mockMvc.perform(get("/api/newsletter"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(blogPostService);
    }

    private void assertNotRouted(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requestBuilder)
            throws Exception {
        mockMvc.perform(requestBuilder)
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    private BlogPostResponseDTO.PostPageResult pageResult(int size) {
        return BlogPostResponseDTO.PostPageResult.builder()
                .posts(List.of(postSummary()))
                .page(0)
                .size(size)
                .totalPages(1)
                .totalElements(1)
                .hasNext(false)
                .build();
    }

    private BlogPostResponseDTO.PostSearchResult searchResult() {
        return BlogPostResponseDTO.PostSearchResult.builder()
                .query("토익스피킹")
                .posts(List.of(postSummary()))
                .page(0)
                .size(10)
                .totalPages(1)
                .totalElements(1)
                .hasNext(false)
                .build();
    }

    private BlogPostResponseDTO.PostDetailResult detailResult() {
        return BlogPostResponseDTO.PostDetailResult.builder()
                .slug("post-1")
                .title("게시글")
                .summary("요약")
                .contentMarkdown("# 본문")
                .thumbnailUrl("https://example.com/post.png")
                .authorName("토선생")
                .seoTitle("SEO")
                .seoDescription("SEO 설명")
                .publishedAt(PUBLISHED_AT)
                .updatedAt(PUBLISHED_AT)
                .relatedPosts(List.of(BlogPostResponseDTO.RelatedPostSummary.builder()
                        .slug("related-1")
                        .title("관련 글")
                        .summary("관련 요약")
                        .thumbnailUrl("https://example.com/related.png")
                        .publishedAt(PUBLISHED_AT)
                        .build()))
                .build();
    }

    private BlogPostResponseDTO.PostSummary postSummary() {
        return BlogPostResponseDTO.PostSummary.builder()
                .slug("post-1")
                .title("게시글")
                .summary("요약")
                .thumbnailUrl("https://example.com/post.png")
                .authorName("토선생")
                .publishedAt(PUBLISHED_AT)
                .build();
    }
}
