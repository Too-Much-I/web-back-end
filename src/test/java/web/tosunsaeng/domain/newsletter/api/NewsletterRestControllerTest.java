package web.tosunsaeng.domain.newsletter.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import web.tosunsaeng.domain.newsletter.api.support.NewsletterClientIpResolver;
import web.tosunsaeng.domain.newsletter.application.NewsletterService;
import web.tosunsaeng.domain.newsletter.dto.NewsletterResponseDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterExceptionAdvice;
import web.tosunsaeng.domain.newsletter.exception.NewsletterRateLimitException;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.global.error.code.status.ErrorStatus;
import web.tosunsaeng.global.exception.GlobalExceptionAdvice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class NewsletterRestControllerTest {

    private NewsletterService newsletterService;
    private NewsletterClientIpResolver clientIpResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        newsletterService = mock(NewsletterService.class);
        clientIpResolver = mock(NewsletterClientIpResolver.class);
        mockMvc = standaloneSetup(new NewsletterRestController(
                        newsletterService,
                        clientIpResolver))
                .setControllerAdvice(
                        new NewsletterExceptionAdvice(),
                        new GlobalExceptionAdvice())
                .build();
    }

    @Test
    void subscribeReturnsApprovedGenericSuccessWithoutEmailOrInternalIdentity()
            throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        when(newsletterService.subscribe(any(), eq("203.0.113.10")))
                .thenReturn(statusResult("ACTIVE"));

        MvcResult result = mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "User@Example.COM",
                                  "consent": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("NEWSLETTER_200"))
                .andExpect(jsonPath("$.message")
                        .value("뉴스레터 구독 요청이 처리되었습니다."))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.subscriberId").doesNotExist())
                .andExpect(jsonPath("$.result.token").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("User@Example.COM");
    }

    @Test
    void unsubscribeAcceptsOnlyJsonBodyAndReturnsNoStoreGenericSuccess()
            throws Exception {
        when(newsletterService.unsubscribe(any()))
                .thenReturn(statusResult("UNSUBSCRIBED"));

        MvcResult result = mockMvc.perform(post("/api/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"signed-sensitive-token\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("NEWSLETTER_201"))
                .andExpect(jsonPath("$.result.status").value("UNSUBSCRIBED"))
                .andExpect(jsonPath("$.result.token").doesNotExist())
                .andExpect(jsonPath("$.result.email").doesNotExist())
                .andExpect(jsonPath("$.result.subscriberId").doesNotExist())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("signed-sensitive-token");
    }

    @Test
    void rateLimitReturnsNewsletter429RetryAfterWithoutInternalScope()
            throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        when(newsletterService.subscribe(any(), eq("203.0.113.10")))
                .thenThrow(new NewsletterRateLimitException(
                        321,
                        NewsletterRateLimitScope.IP_DAILY));

        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"consent\":true}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "321"))
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4290"))
                .andExpect(jsonPath("$.result.retryAfterSeconds").value(321))
                .andExpect(jsonPath("$.result.limitScope").doesNotExist())
                .andExpect(jsonPath("$.result.redisKey").doesNotExist());
    }

    @Test
    void emailConsentAndBouncedErrorsUseFixedNewsletterResponses() throws Exception {
        when(clientIpResolver.resolve(any())).thenReturn("203.0.113.10");
        when(newsletterService.subscribe(any(), eq("203.0.113.10")))
                .thenThrow(
                        new NewsletterException(ErrorStatus._NEWSLETTER_EMAIL_INVALID),
                        new NewsletterException(ErrorStatus._NEWSLETTER_CONSENT_REQUIRED),
                        new NewsletterException(
                                ErrorStatus._NEWSLETTER_SUBSCRIPTION_UNAVAILABLE));

        performSubscribe("invalid", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4002"))
                .andExpect(jsonPath("$.result").doesNotExist());
        performSubscribe("user@example.com", false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4004"));
        performSubscribe("user@example.com", true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4091"))
                .andExpect(jsonPath("$.message")
                        .value("뉴스레터 구독 요청을 처리할 수 없습니다."));
    }

    @Test
    void malformedJsonNeverCallsResolverOrService() throws Exception {
        mockMvc.perform(post("/api/newsletter/subscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"));

        verifyNoInteractions(clientIpResolver, newsletterService);
    }

    @Test
    void queryParameterOnlyUnsubscribeIsRejectedWithoutStateChange()
            throws Exception {
        mockMvc.perform(post("/api/newsletter/unsubscribe")
                        .queryParam("token", "query-sensitive-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4005"))
                .andExpect(jsonPath("$.result").doesNotExist());

        verifyNoInteractions(newsletterService);
    }

    @Test
    void invalidBodyTokenUsesOneGenericErrorWithoutEcho() throws Exception {
        when(newsletterService.unsubscribe(any()))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID));

        MvcResult result = mockMvc.perform(post("/api/newsletter/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"forged-sensitive-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("NEWSLETTER_4005"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("forged-sensitive-token");
    }

    @Test
    void getUnsubscribeDoesNotExistAndCannotCallService() throws Exception {
        assertNotRouted(get("/api/newsletter/unsubscribe"));
        verifyNoInteractions(newsletterService);
    }

    @Test
    void verifyAndInternalNewsletterApisDoNotExist() throws Exception {
        assertNotRouted(get("/api/newsletter/verify"));
        assertNotRouted(post("/api/newsletter/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        assertNotRouted(post("/internal/newsletter/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
        verifyNoInteractions(newsletterService);
    }

    private org.springframework.test.web.servlet.ResultActions performSubscribe(
            String email,
            boolean consent) throws Exception {
        return mockMvc.perform(post("/api/newsletter/subscribe")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"consent\":" + consent + "}"));
    }

    private void assertNotRouted(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request)
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));
    }

    private NewsletterResponseDTO.StatusResult statusResult(String status) {
        return NewsletterResponseDTO.StatusResult.builder()
                .status(status)
                .build();
    }
}
