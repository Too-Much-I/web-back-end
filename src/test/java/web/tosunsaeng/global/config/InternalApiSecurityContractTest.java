package web.tosunsaeng.global.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import web.tosunsaeng.domain.blog.application.BlogPostService;
import web.tosunsaeng.domain.blog.dto.BlogPostResponseDTO;
import web.tosunsaeng.domain.comment.application.BlogCommentModerationService;
import web.tosunsaeng.domain.comment.application.BlogCommentService;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;
import web.tosunsaeng.domain.comment.dto.BlogCommentResponseDTO;
import web.tosunsaeng.domain.exams.application.ExamService;
import web.tosunsaeng.domain.exams.dto.ExamResponseDTO;
import web.tosunsaeng.domain.newsletter.application.NewsletterOperationsService;
import web.tosunsaeng.domain.newsletter.application.NewsletterService;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.global.config.security.InternalApiKeyAuthenticationFilter;
import web.tosunsaeng.global.config.security.InternalApiProperties;
import web.tosunsaeng.global.config.security.JwtAuthenticationFilter;
import web.tosunsaeng.global.config.security.JwtTokenProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "internal.api.enabled=true",
        "internal.api.key=test-only-internal-api-key-32-bytes-minimum"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class InternalApiSecurityContractTest {

    private static final String KEY =
            "test-only-internal-api-key-32-bytes-minimum";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InternalApiProperties properties;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private BlogCommentModerationService moderationService;

    @MockitoBean
    private NewsletterOperationsService operationsService;

    @MockitoBean
    private BlogPostService blogPostService;

    @MockitoBean
    private BlogCommentService commentService;

    @MockitoBean
    private NewsletterService newsletterService;

    @MockitoBean
    private ExamService examService;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        when(moderationService.getComments(any()))
                .thenReturn(emptyModerationPage());
    }

    @Test
    void disabledInternalApiReturnsNotFoundEvenWhenKeyIsPresent() throws Exception {
        properties.setEnabled(false);
        try {
            mockMvc.perform(get("/internal/comments")
                            .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, KEY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON404"))
                    .andExpect(jsonPath("$.result").doesNotExist());
            mockMvc.perform(post("/internal/newsletter/posts/post-id/cancel")
                            .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, KEY))
                    .andExpect(status().isNotFound());
            verify(moderationService, never()).getComments(any());
            verify(operationsService, never()).cancelScheduledCampaignByPostId(
                    anyString());
        } finally {
            properties.setEnabled(true);
        }
    }

    @Test
    void missingAndWrongKeysHaveTheSameUnauthorizedResponse() throws Exception {
        String missing = mockMvc.perform(get("/internal/comments"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String wrong = mockMvc.perform(get("/internal/comments")
                        .header(
                                InternalApiKeyAuthenticationFilter.HEADER_NAME,
                                "wrong-key-value"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrong).isEqualTo(missing).doesNotContain("wrong-key-value");
        verify(moderationService, never()).getComments(any());
    }

    @Test
    void duplicateOrAbnormallyLongKeyHeadersAreRejected() throws Exception {
        mockMvc.perform(get("/internal/comments")
                        .header(
                                InternalApiKeyAuthenticationFilter.HEADER_NAME,
                                KEY,
                                KEY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/comments")
                        .header(
                                InternalApiKeyAuthenticationFilter.HEADER_NAME,
                                "x".repeat(1_025)))
                .andExpect(status().isUnauthorized());

        verify(moderationService, never()).getComments(any());
    }

    @Test
    void validKeyCreatesOnlyFixedInternalAuthentication() throws Exception {
        AtomicReference<Authentication> captured = new AtomicReference<>();
        when(moderationService.getComments(any())).thenAnswer(invocation -> {
            captured.set(SecurityContextHolder.getContext().getAuthentication());
            return emptyModerationPage();
        });

        mockMvc.perform(get("/internal/comments")
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, KEY))
                .andExpect(status().isOk());

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getPrincipal())
                .isEqualTo(InternalApiKeyAuthenticationFilter.INTERNAL_PRINCIPAL);
        assertThat(captured.get().getCredentials()).isNull();
        assertThat(captured.get().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .containsExactly(InternalApiKeyAuthenticationFilter.INTERNAL_AUTHORITY);
    }

    @Test
    void jwtAloneCannotEnterInternalChain() throws Exception {
        String jwt = jwtTokenProvider.createToken("user-id");

        mockMvc.perform(get("/internal/comments")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isUnauthorized());

        verify(moderationService, never()).getComments(any());
    }

    @Test
    void internalChainHasHigherPriorityAndDoesNotContainJwtFilter() {
        List<SecurityFilterChain> chains = springSecurityFilterChain.getFilterChains();
        int internalIndex = indexOfFilter(chains, InternalApiKeyAuthenticationFilter.class);
        int jwtIndex = indexOfFilter(chains, JwtAuthenticationFilter.class);

        assertThat(internalIndex).isGreaterThanOrEqualTo(0).isLessThan(jwtIndex);
        MockHttpServletRequest internal = new MockHttpServletRequest("GET", "/internal/comments");
        MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/api/posts");
        assertThat(chains.get(internalIndex).matches(internal)).isTrue();
        assertThat(chains.get(internalIndex).matches(publicRequest)).isFalse();
        assertThat(chains.get(internalIndex).getFilters())
                .noneMatch(JwtAuthenticationFilter.class::isInstance);
    }

    @Test
    void representativePublicApisRemainAccessibleWithoutInternalKey()
            throws Exception {
        when(blogPostService.getPublicPosts(0, 10)).thenReturn(emptyPostPage());
        when(commentService.getComments("post-slug", 0, 20))
                .thenReturn(emptyCommentPage());
        when(commentService.createComment(
                anyString(), any(), isNull(), any()))
                .thenReturn(new BlogCommentService.CreatedCommentSession(
                        BlogCommentResponseDTO.CreatedCommentResult.builder()
                                .id("comment-id")
                                .content("공개 댓글")
                                .build(),
                        null));
        when(newsletterService.subscribe(any(), anyString()))
                .thenReturn(newsletterStatus("ACTIVE"));
        when(newsletterService.unsubscribe(any()))
                .thenReturn(newsletterStatus("UNSUBSCRIBED"));
        when(examService.createExamSession())
                .thenReturn(ExamResponseDTO.CreateSessionResult.builder()
                        .examId("exam-id")
                        .questions(List.of())
                        .build());

        mockMvc.perform(get("/api/posts")
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, KEY))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/post-slug/comments"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/post-slug/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"공개 댓글\",\"website\":\"\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.test\",\"consent\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"opaque-token\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/opaque-token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("List-Unsubscribe", "One-Click"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/exams"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void internalPatchUsesOnlyInternalChainCsrfPolicy() throws Exception {
        when(moderationService.restore("comment-id"))
                .thenReturn(BlogCommentModerationDTO.ModerationTransitionResult.builder()
                        .id("comment-id")
                        .status(web.tosunsaeng.domain.comment.domain.enums.CommentStatus.VISIBLE)
                        .build());

        mockMvc.perform(patch("/internal/comments/comment-id/restore")
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, KEY))
                .andExpect(status().isOk());
    }

    @Test
    void publicOpenApiDocumentDoesNotExposeInternalControllers() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(document)
                .doesNotContain("/internal/comments")
                .doesNotContain("/internal/newsletter");
    }

    @Test
    void forbiddenCommentMutationsAndOneClickGetRemainUnavailable()
            throws Exception {
        mockMvc.perform(delete("/api/comments/comment-id"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/comments/comment-id"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(
                        "/api/newsletter/one-click-unsubscribe/opaque-token"))
                .andExpect(status().isMethodNotAllowed());

        verify(newsletterService, never()).unsubscribe(any());
    }

    @Test
    void authenticationFailuresDoNotLogKeyOrHeaderValue(
            CapturedOutput output) throws Exception {
        String wrong = "wrong-sensitive-internal-key-material";

        mockMvc.perform(get("/internal/comments")
                        .header(InternalApiKeyAuthenticationFilter.HEADER_NAME, wrong))
                .andExpect(status().isUnauthorized());

        assertThat(output.getAll())
                .doesNotContain(KEY)
                .doesNotContain(wrong);
    }

    private int indexOfFilter(
            List<SecurityFilterChain> chains,
            Class<?> filterType) {
        for (int index = 0; index < chains.size(); index++) {
            if (chains.get(index).getFilters().stream().anyMatch(filterType::isInstance)) {
                return index;
            }
        }
        return -1;
    }

    private BlogCommentModerationDTO.ModeratedCommentPageResult emptyModerationPage() {
        return BlogCommentModerationDTO.ModeratedCommentPageResult.builder()
                .comments(List.of())
                .page(0)
                .size(20)
                .totalPages(0)
                .totalElements(0)
                .hasNext(false)
                .build();
    }

    private BlogPostResponseDTO.PostPageResult emptyPostPage() {
        return BlogPostResponseDTO.PostPageResult.builder()
                .posts(List.of())
                .page(0)
                .size(10)
                .totalPages(0)
                .totalElements(0)
                .hasNext(false)
                .build();
    }

    private BlogCommentResponseDTO.CommentPageResult emptyCommentPage() {
        return BlogCommentResponseDTO.CommentPageResult.builder()
                .comments(List.of())
                .page(0)
                .size(20)
                .totalPages(0)
                .totalElements(0)
                .hasNext(false)
                .build();
    }

    private NewsletterResponseDTO.StatusResult newsletterStatus(String status) {
        return NewsletterResponseDTO.StatusResult.builder()
                .status(status)
                .build();
    }
}
