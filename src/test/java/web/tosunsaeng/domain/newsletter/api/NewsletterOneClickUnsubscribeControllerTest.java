package web.tosunsaeng.domain.newsletter.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import web.tosunsaeng.domain.newsletter.application.NewsletterService;
import web.tosunsaeng.domain.newsletter.dto.NewsletterRequestDTO;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.domain.newsletter.exception.NewsletterExceptionAdvice;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NewsletterOneClickUnsubscribeControllerTest {

    private static final String TOKEN = "opaque.signed-token";

    @Mock
    private NewsletterService newsletterService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new NewsletterOneClickUnsubscribeController(newsletterService))
                .setControllerAdvice(new NewsletterExceptionAdvice())
                .build();
    }

    @Test
    void validFormPostUnsubscribesWithoutRedirectOrSensitiveResponse() throws Exception {
        MvcResult result = mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("List-Unsubscribe", "One-Click"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(redirectedUrl((String) null))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).isEmpty();
        verify(newsletterService).unsubscribe(argThat(request ->
                TOKEN.equals(request.getToken())));
    }

    @Test
    void multipartFormIsAcceptedWithTheSameExactField() throws Exception {
        mockMvc.perform(multipart(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .param("List-Unsubscribe", "One-Click"))
                .andExpect(status().isNoContent());

        verify(newsletterService).unsubscribe(argThat(request ->
                TOKEN.equals(request.getToken())));
    }

    @Test
    void missingOrWrongFormFieldNeverChangesState() throws Exception {
        mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("List-Unsubscribe", "one-click"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(newsletterService);
    }

    @Test
    void jsonContractIsNotMixedIntoOneClickEndpoint() throws Exception {
        mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"List-Unsubscribe\":\"One-Click\"}"))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(newsletterService);
    }

    @Test
    void getHasNoMappingAndCannotChangeState() throws Exception {
        mockMvc.perform(get(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isIn(404, 405));

        verifyNoInteractions(newsletterService);
    }

    @Test
    void invalidTokenUsesGenericErrorWithoutEcho() throws Exception {
        when(newsletterService.unsubscribe(org.mockito.ArgumentMatchers.any(
                NewsletterRequestDTO.UnsubscribeRequest.class)))
                .thenThrow(new NewsletterException(
                        ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID));

        MvcResult result = mockMvc.perform(post(
                        "/api/newsletter/one-click-unsubscribe/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("List-Unsubscribe", "One-Click"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain(TOKEN);
        verify(newsletterService, never()).subscribe(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
