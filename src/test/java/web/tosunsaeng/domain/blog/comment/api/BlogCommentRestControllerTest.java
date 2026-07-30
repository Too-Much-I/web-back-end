package web.tosunsaeng.domain.blog.comment.api;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import web.tosunsaeng.domain.blog.comment.api.support.AnonymousCookieFactory;
import web.tosunsaeng.domain.blog.comment.application.BlogCommentService;
import web.tosunsaeng.domain.blog.comment.config.AnonymousSessionProperties;
import web.tosunsaeng.domain.blog.comment.domain.enums.CommentRule;
import web.tosunsaeng.domain.blog.comment.domain.policy.CommentValidator;
import web.tosunsaeng.domain.blog.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentException;
import web.tosunsaeng.domain.blog.comment.exception.BlogCommentExceptionAdvice;
import web.tosunsaeng.domain.blog.comment.exception.CommentValidationException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class BlogCommentRestControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-30T02:00:00Z");
    private static final String AVATAR_URL =
            "https://cdn.example.test/character-image/example-otter-v1.webp";

    private BlogCommentService blogCommentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        blogCommentService = mock(BlogCommentService.class);
        AnonymousCookieFactory cookieFactory = new AnonymousCookieFactory(sessionProperties());
        mockMvc = standaloneSetup(new BlogCommentRestController(
                        blogCommentService,
                        cookieFactory))
                .setControllerAdvice(
                        new BlogCommentExceptionAdvice(),
                        new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void returnsCommentListBaseResponseWithApprovedPublicFields() throws Exception {
        when(blogCommentService.getComments("public-post", 0, 20))
                .thenReturn(commentPage(20));

        mockMvc.perform(get("/api/posts/public-post/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMENT_200"))
                .andExpect(jsonPath("$.message").value("댓글 목록을 조회했습니다."))
                .andExpect(jsonPath("$.result.comments[0].id").value("comment-1"))
                .andExpect(jsonPath("$.result.comments[0].nickname").value("차분한 수달"))
                .andExpect(jsonPath("$.result.comments[0].avatarSeed").value("seed-1"))
                .andExpect(jsonPath("$.result.comments[0].avatarImageUrl").value(AVATAR_URL))
                .andExpect(jsonPath("$.result.comments[0].content").value("정상 댓글"))
                .andExpect(jsonPath("$.result.comments[0].avatarImageKey").doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].anonymousVisitorId").doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].tokenHash").doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].isMine").doesNotExist())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalPages").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.hasNext").value(false));

        verify(blogCommentService).getComments("public-post", 0, 20);
    }

    @Test
    void createsCommentWith201BaseResponseAndApprovedCookie() throws Exception {
        when(blogCommentService.createComment(eq("public-post"), any(), eq(null)))
                .thenReturn(new BlogCommentService.CreatedCommentSession(
                        createdComment(),
                        "new-raw-token"));

        MvcResult mvcResult = mockMvc.perform(post("/api/posts/public-post/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "  정상 댓글  ",
                                  "website": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMENT_201"))
                .andExpect(jsonPath("$.message").value("댓글을 작성했습니다."))
                .andExpect(jsonPath("$.result.id").value("comment-1"))
                .andExpect(jsonPath("$.result.nickname").value("차분한 수달"))
                .andExpect(jsonPath("$.result.avatarSeed").value("seed-1"))
                .andExpect(jsonPath("$.result.avatarImageUrl").value(AVATAR_URL))
                .andExpect(jsonPath("$.result.avatarImageKey").doesNotExist())
                .andExpect(jsonPath("$.result.content").value("정상 댓글"))
                .andExpect(header().string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("anon_session=new-raw-token"),
                                org.hamcrest.Matchers.containsString("Path=/"),
                                org.hamcrest.Matchers.containsString("Max-Age=2592000"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andReturn();

        assertThat(mvcResult.getResponse().getHeader("Set-Cookie"))
                .doesNotContain("Secure");
        assertThat(mvcResult.getResponse().getContentAsString())
                .doesNotContain("new-raw-token")
                .doesNotContain("tokenHash")
                .doesNotContain("avatarImageKey");
    }

    @Test
    void existingCookieIsPassedToServiceWithoutBeingReissued() throws Exception {
        when(blogCommentService.createComment(eq("public-post"), any(), eq("existing-token")))
                .thenReturn(new BlogCommentService.CreatedCommentSession(
                        createdComment(),
                        null));

        mockMvc.perform(post("/api/posts/public-post/comments")
                        .cookie(new Cookie("anon_session", "existing-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"정상 댓글\",\"website\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Set-Cookie"));

        verify(blogCommentService).createComment(
                eq("public-post"),
                any(),
                eq("existing-token"));
    }

    @Test
    void returnsStructuredSortedValidationViolations() throws Exception {
        List<CommentValidator.Violation> violations = List.of(
                violation(CommentRule.COMMENT_MIN_LENGTH),
                violation(CommentRule.COMMENT_EMPTY));
        when(blogCommentService.createComment(eq("public-post"), any(), eq(null)))
                .thenThrow(new CommentValidationException(
                        ErrorStatus._COMMENT_VALIDATION_FAILED,
                        violations));

        mockMvc.perform(post("/api/posts/public-post/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMENT_4001"))
                .andExpect(jsonPath("$.message")
                        .value("댓글 작성 규칙을 확인해 주세요."))
                .andExpect(jsonPath("$.result.violations.length()").value(2))
                .andExpect(jsonPath("$.result.violations[0].ruleNumber").value(1))
                .andExpect(jsonPath("$.result.violations[0].ruleCode")
                        .value("COMMENT_MIN_LENGTH"))
                .andExpect(jsonPath("$.result.violations[1].ruleNumber").value(8))
                .andExpect(jsonPath("$.result.violations[1].ruleCode")
                        .value("COMMENT_EMPTY"));
    }

    @Test
    void malformedJsonUsesCommentScopedPlainTextRuleResponse() throws Exception {
        mockMvc.perform(post("/api/posts/public-post/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_4001"))
                .andExpect(jsonPath("$.result.violations.length()").value(1))
                .andExpect(jsonPath("$.result.violations[0].ruleNumber").value(4))
                .andExpect(jsonPath("$.result.violations[0].ruleCode")
                        .value("COMMENT_PLAIN_TEXT_ONLY"));

        verifyNoInteractions(blogCommentService);
    }

    @Test
    void returnsSameNotFoundResponseForUnavailablePostCommentList() throws Exception {
        when(blogCommentService.getComments("hidden-post", 0, 20))
                .thenThrow(new BlogCommentException(ErrorStatus._BLOG_POST_NOT_FOUND));

        mockMvc.perform(get("/api/posts/hidden-post/comments"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("BLOG_4004"))
                .andExpect(jsonPath("$.message").value("게시글을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsApprovedPaginationErrorsAndAcceptsSizeOneHundred() throws Exception {
        when(blogCommentService.getComments("public-post", -1, 20))
                .thenThrow(new BlogCommentException(ErrorStatus._BLOG_PAGE_NEGATIVE));
        when(blogCommentService.getComments("public-post", 0, 0))
                .thenThrow(new BlogCommentException(ErrorStatus._BLOG_SIZE_TOO_SMALL));
        when(blogCommentService.getComments("public-post", 0, 101))
                .thenThrow(new BlogCommentException(ErrorStatus._BLOG_SIZE_TOO_LARGE));
        when(blogCommentService.getComments("public-post", 0, 100))
                .thenReturn(commentPage(100));

        mockMvc.perform(get("/api/posts/public-post/comments").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4003"));
        mockMvc.perform(get("/api/posts/public-post/comments").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4005"));
        mockMvc.perform(get("/api/posts/public-post/comments").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
        mockMvc.perform(get("/api/posts/public-post/comments").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BLOG_4006"));
    }

    @Test
    void regeneratesProfileWith200AndReturnsImageUrlWithoutInternalKey() throws Exception {
        when(blogCommentService.regenerateAnonymousProfile(null))
                .thenReturn(new BlogCommentService.AnonymousProfileSession(
                        BlogCommentResponseDTO.AnonymousProfileResult.builder()
                                .nickname("명랑한 펭귄")
                                .avatarSeed("seed-2")
                                .avatarImageUrl(
                                        "https://cdn.example.test/character-image/"
                                                + "example-penguin-v1.webp")
                                .build(),
                        "profile-raw-token"));

        MvcResult mvcResult = mockMvc.perform(post("/api/comments/nickname/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMENT_202"))
                .andExpect(jsonPath("$.result.nickname").value("명랑한 펭귄"))
                .andExpect(jsonPath("$.result.avatarSeed").value("seed-2"))
                .andExpect(jsonPath("$.result.avatarImageUrl")
                        .value("https://cdn.example.test/character-image/"
                                + "example-penguin-v1.webp"))
                .andExpect(jsonPath("$.result.avatarImageKey").doesNotExist())
                .andExpect(header().string(
                        "Set-Cookie",
                        org.hamcrest.Matchers.containsString(
                                "anon_session=profile-raw-token")))
                .andReturn();

        assertThat(mvcResult.getResponse().getContentAsString())
                .doesNotContain("profile-raw-token")
                .doesNotContain("avatarImageKey")
                .doesNotContain("tokenHash");
    }

    @Test
    void doesNotExposeCommentPatchApi() throws Exception {
        assertNotRouted(patch("/api/comments/comment-1"));
        verifyNoInteractions(blogCommentService);
    }

    @Test
    void doesNotExposeCommentDeleteApi() throws Exception {
        assertNotRouted(delete("/api/comments/comment-1"));
        verifyNoInteractions(blogCommentService);
    }

    @Test
    void doesNotExposePostWriteNewsletterOrOperatorCommentApis() throws Exception {
        assertNotRouted(post("/api/posts"));
        assertNotRouted(get("/api/newsletter"));
        assertNotRouted(post("/internal/comments/comment-1/hide"));
        verifyNoInteractions(blogCommentService);
    }

    private void assertNotRouted(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    private BlogCommentResponseDTO.CommentPageResult commentPage(int size) {
        return BlogCommentResponseDTO.CommentPageResult.builder()
                .comments(List.of(BlogCommentResponseDTO.CommentSummary.builder()
                        .id("comment-1")
                        .nickname("차분한 수달")
                        .avatarSeed("seed-1")
                        .avatarImageUrl(AVATAR_URL)
                        .content("정상 댓글")
                        .createdAt(CREATED_AT)
                        .build()))
                .page(0)
                .size(size)
                .totalPages(1)
                .totalElements(1)
                .hasNext(false)
                .build();
    }

    private BlogCommentResponseDTO.CreatedCommentResult createdComment() {
        return BlogCommentResponseDTO.CreatedCommentResult.builder()
                .id("comment-1")
                .nickname("차분한 수달")
                .avatarSeed("seed-1")
                .avatarImageUrl(AVATAR_URL)
                .content("정상 댓글")
                .createdAt(CREATED_AT)
                .build();
    }

    private CommentValidator.Violation violation(CommentRule rule) {
        return new CommentValidator.Violation(
                rule.getRuleNumber(),
                rule.getRuleCode(),
                rule.getDefaultMessage());
    }

    private AnonymousSessionProperties sessionProperties() {
        AnonymousSessionProperties properties = new AnonymousSessionProperties();
        properties.setTokenSecret("test-only-anonymous-token-secret-at-least-32-bytes");
        properties.setCookieSecure(false);
        properties.setCookieMaxAgeDays(30);
        properties.afterPropertiesSet();
        return properties;
    }
}
