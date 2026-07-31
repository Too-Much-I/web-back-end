package web.tosunsaeng.domain.comment.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import web.tosunsaeng.domain.comment.application.BlogCommentModerationService;
import web.tosunsaeng.domain.comment.domain.enums.CommentStatus;
import web.tosunsaeng.domain.comment.domain.enums.HiddenReason;
import web.tosunsaeng.domain.comment.dto.BlogCommentModerationDTO;
import web.tosunsaeng.domain.comment.exception.BlogCommentException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.InternalOperationsExceptionAdvice;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalCommentControllerTest {

    private static final Instant FROM = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant HIDDEN_AT = Instant.parse("2026-07-31T05:00:00Z");

    @Mock
    private BlogCommentModerationService moderationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalCommentController(moderationService))
                .setControllerAdvice(new InternalOperationsExceptionAdvice())
                .build();
    }

    @Test
    void internalControllerIsHiddenFromOpenApi() {
        assertThat(InternalCommentController.class.isAnnotationPresent(Hidden.class))
                .isTrue();
    }

    @Test
    void listsWithAllFiltersHalfOpenPeriodAndSafeFields() throws Exception {
        when(moderationService.getComments(any())).thenReturn(pageResult());

        mockMvc.perform(get("/internal/comments")
                        .param("status", "HIDDEN")
                        .param("postId", "post-id")
                        .param("createdAtFrom", FROM.toString())
                        .param("createdAtTo", TO.toString())
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.comments[0].commentId")
                        .value("comment-id"))
                .andExpect(jsonPath("$.result.comments[0].postId")
                        .value("post-id"))
                .andExpect(jsonPath("$.result.comments[0].postSlug")
                        .value("post-slug"))
                .andExpect(jsonPath("$.result.comments[0].content")
                        .value("운영 조회 댓글"))
                .andExpect(jsonPath("$.result.comments[0].anonymousVisitorId")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].avatarSeed")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].avatarImageKey")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.comments[0].updatedAt")
                        .doesNotExist());

        ArgumentCaptor<BlogCommentModerationDTO.CommentFilter> captor =
                ArgumentCaptor.forClass(BlogCommentModerationDTO.CommentFilter.class);
        org.mockito.Mockito.verify(moderationService).getComments(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(captor.getValue().getPostId()).isEqualTo("post-id");
        assertThat(captor.getValue().getCreatedAtFrom()).isEqualTo(FROM);
        assertThat(captor.getValue().getCreatedAtTo()).isEqualTo(TO);
        assertThat(captor.getValue().getPage()).isOne();
        assertThat(captor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void listDefaultsToPageZeroAndSizeTwentyAndPassesSlug() throws Exception {
        when(moderationService.getComments(any())).thenReturn(emptyPage());

        mockMvc.perform(get("/internal/comments").param("slug", "post-slug"))
                .andExpect(status().isOk());

        ArgumentCaptor<BlogCommentModerationDTO.CommentFilter> captor =
                ArgumentCaptor.forClass(BlogCommentModerationDTO.CommentFilter.class);
        org.mockito.Mockito.verify(moderationService).getComments(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("post-slug");
        assertThat(captor.getValue().getPage()).isZero();
        assertThat(captor.getValue().getSize()).isEqualTo(20);
    }

    @Test
    void invalidEnumDateAndServiceValidationAreSafeBadRequests() throws Exception {
        mockMvc.perform(get("/internal/comments").param("status", "DELETED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result").doesNotExist());
        mockMvc.perform(get("/internal/comments")
                        .param("createdAtFrom", "not-an-instant"))
                .andExpect(status().isBadRequest());

        when(moderationService.getComments(any())).thenThrow(
                new BlogCommentException(
                        ErrorStatus._COMMENT_MODERATION_INVALID_REQUEST));
        mockMvc.perform(get("/internal/comments").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_4002"));
    }

    @Test
    void hidesVisibleOrPendingCommentWithAllowedReason() throws Exception {
        when(moderationService.hide("comment-id", HiddenReason.SPAM))
                .thenReturn(hiddenTransition());

        mockMvc.perform(patch("/internal/comments/comment-id/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("comment-id"))
                .andExpect(jsonPath("$.result.status").value("HIDDEN"))
                .andExpect(jsonPath("$.result.hiddenReason").value("SPAM"))
                .andExpect(jsonPath("$.result.hiddenAt").exists())
                .andExpect(jsonPath("$.result.content").doesNotExist());
    }

    @Test
    void unknownOrMissingHiddenReasonIsBadRequest() throws Exception {
        mockMvc.perform(patch("/internal/comments/comment-id/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/internal/comments/comment-id/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restoreReturnsClearedHiddenMetadata() throws Exception {
        when(moderationService.restore("comment-id"))
                .thenReturn(BlogCommentModerationDTO.ModerationTransitionResult.builder()
                        .id("comment-id")
                        .status(CommentStatus.VISIBLE)
                        .hiddenReason(null)
                        .hiddenAt(null)
                        .build());

        mockMvc.perform(patch("/internal/comments/comment-id/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value("comment-id"))
                .andExpect(jsonPath("$.result.status").value("VISIBLE"))
                .andExpect(jsonPath("$.result.hiddenReason").value(nullValue()))
                .andExpect(jsonPath("$.result.hiddenAt").value(nullValue()));
    }

    @Test
    void missingAndConflictingTransitionsMapTo404And409() throws Exception {
        when(moderationService.hide("missing", HiddenReason.SPAM))
                .thenThrow(new BlogCommentException(ErrorStatus._COMMENT_NOT_FOUND));
        when(moderationService.restore("visible"))
                .thenThrow(new BlogCommentException(
                        ErrorStatus._COMMENT_STATE_CONFLICT));

        mockMvc.perform(patch("/internal/comments/missing/hide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/internal/comments/visible/restore"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteEndpointDoesNotExist() throws Exception {
        mockMvc.perform(delete("/internal/comments/comment-id"))
                .andExpect(status().isNotFound());
    }

    private BlogCommentModerationDTO.ModeratedCommentPageResult pageResult() {
        return BlogCommentModerationDTO.ModeratedCommentPageResult.builder()
                .comments(List.of(BlogCommentModerationDTO.ModeratedCommentResult.builder()
                        .commentId("comment-id")
                        .postId("post-id")
                        .postSlug("post-slug")
                        .nickname("차분한 수달")
                        .avatarImageUrl("https://cdn.example.test/otter.webp")
                        .content("운영 조회 댓글")
                        .status(CommentStatus.HIDDEN)
                        .hiddenReason(HiddenReason.SPAM)
                        .createdAt(FROM)
                        .hiddenAt(HIDDEN_AT)
                        .build()))
                .page(1)
                .size(20)
                .totalPages(2)
                .totalElements(21)
                .hasNext(false)
                .build();
    }

    private BlogCommentModerationDTO.ModeratedCommentPageResult emptyPage() {
        return BlogCommentModerationDTO.ModeratedCommentPageResult.builder()
                .comments(List.of())
                .page(0)
                .size(20)
                .totalPages(0)
                .totalElements(0)
                .hasNext(false)
                .build();
    }

    private BlogCommentModerationDTO.ModerationTransitionResult hiddenTransition() {
        return BlogCommentModerationDTO.ModerationTransitionResult.builder()
                .id("comment-id")
                .status(CommentStatus.HIDDEN)
                .hiddenReason(HiddenReason.SPAM)
                .hiddenAt(HIDDEN_AT)
                .build();
    }
}
