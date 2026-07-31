package web.tosunsaeng.domain.newsletter.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import web.tosunsaeng.domain.newsletter.application.NewsletterOperationsService;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterFailureType;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailSendException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.InternalOperationsExceptionAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalNewsletterControllerTest {

    @Mock
    private NewsletterOperationsService operationsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalNewsletterController(operationsService))
                .setControllerAdvice(new InternalOperationsExceptionAdvice())
                .build();
    }

    @Test
    void internalControllerIsHiddenFromOpenApi() {
        assertThat(InternalNewsletterController.class.isAnnotationPresent(Hidden.class))
                .isTrue();
    }

    @Test
    void testSendReturnsOnlyPostAndStatusWithoutEmail() throws Exception {
        when(operationsService.sendTest("post-id", "allowed@example.test"))
                .thenReturn(NewsletterOperationsService.TestSendResult.SENT);

        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"allowed@example.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.postId").value("post-id"))
                .andExpect(jsonPath("$.result.status").value("SENT"))
                .andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "allowed@example.test"))));
    }

    @Test
    void blankOrInvalidTestEmailIsBadRequestWithoutEcho() throws Exception {
        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result").doesNotExist());

        when(operationsService.sendTest("post-id", "invalid-email"))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_EMAIL_INVALID));
        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "invalid-email"))));
    }

    @Test
    void allowlistViolationIsForbiddenWithoutRecipientOrAllowlist() throws Exception {
        when(operationsService.sendTest("post-id", "denied@example.test"))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_TEST_RECIPIENT_FORBIDDEN));

        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"denied@example.test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4030"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "denied@example.test"))));
    }

    @Test
    void disabledSwitchIsConflictAndProviderIsNotReportedAsSent() throws Exception {
        when(operationsService.sendTest("post-id", "allowed@example.test"))
                .thenReturn(NewsletterOperationsService.TestSendResult.DISABLED);

        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"allowed@example.test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4093"));
    }

    @Test
    void providerFailureIsSanitizedBadGateway() throws Exception {
        when(operationsService.sendTest("post-id", "allowed@example.test"))
                .thenThrow(new NewsletterEmailSendException(
                        NewsletterFailureType.TRANSIENT_PROVIDER,
                        true,
                        true));

        mockMvc.perform(post("/internal/newsletter/posts/post-id/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"allowed@example.test\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_5020"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "allowed@example.test"))));
    }

    @Test
    void cancelUsesPostIdAndReturnsCanceledState() throws Exception {
        mockMvc.perform(post("/internal/newsletter/posts/post-id/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.postId").value("post-id"))
                .andExpect(jsonPath("$.result.status").value("CANCELED"));

        verify(operationsService).cancelScheduledCampaignByPostId("post-id");
    }

    @Test
    void cancelMissingAndStateConflictMapTo404And409() throws Exception {
        whenCancelThrows("missing", ErrorStatus._NEWSLETTER_OPERATION_NOT_FOUND);
        whenCancelThrows("sending", ErrorStatus._NEWSLETTER_OPERATION_CONFLICT);

        mockMvc.perform(post("/internal/newsletter/posts/missing/cancel"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/internal/newsletter/posts/sending/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    void retryReturnsBoundedCountsAndHasMoreWithoutDeliveryDetails() throws Exception {
        when(operationsService.retryFailedDeliveriesByPostId("post-id"))
                .thenReturn(new NewsletterOperationsService.ManualRetryBatchResult(
                        98,
                        2,
                        true));

        mockMvc.perform(post("/internal/newsletter/posts/post-id/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.postId").value("post-id"))
                .andExpect(jsonPath("$.result.retriedCount").value(98))
                .andExpect(jsonPath("$.result.skippedCount").value(2))
                .andExpect(jsonPath("$.result.hasMore").value(true))
                .andExpect(jsonPath("$.result.deliveryId").doesNotExist())
                .andExpect(jsonPath("$.result.subscriberId").doesNotExist());
    }

    @Test
    void retryWithoutEligibleDeliveryIsConflict() throws Exception {
        when(operationsService.retryFailedDeliveriesByPostId("post-id"))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_OPERATION_CONFLICT));

        mockMvc.perform(post("/internal/newsletter/posts/post-id/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    private void whenCancelThrows(String postId, ErrorStatus status) {
        org.mockito.Mockito.doThrow(new NewsletterException(status))
                .when(operationsService)
                .cancelScheduledCampaignByPostId(postId);
    }
}
