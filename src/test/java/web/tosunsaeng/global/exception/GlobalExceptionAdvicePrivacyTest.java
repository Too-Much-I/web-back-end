package web.tosunsaeng.global.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionAdvicePrivacyTest {

    private static final String SENTINEL =
            "mongodb-secret@example.test unsubscribe-token provider-response";

    private final GlobalExceptionAdvice advice = new GlobalExceptionAdvice();

    @Test
    void unexpectedExceptionReturnsOnlyGenericSafeError(CapturedOutput output) {
        ResponseEntity<Object> response = advice.exception(
                new IllegalStateException(SENTINEL),
                webRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isInstanceOfSatisfying(
                BaseResponse.class,
                body -> {
                    assertThat(body.getCode()).isEqualTo("COMMON500");
                    assertThat(body.getMessage())
                            .isEqualTo("서버 에러, 관리자에게 문의 바랍니다.")
                            .doesNotContain(SENTINEL);
                    assertThat(body.getResult()).isNull();
                });
        assertThat(output.getAll()).doesNotContain(SENTINEL);
    }

    @Test
    void malformedJsonReturnsOnlyGenericBadRequest(CapturedOutput output) {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException(
                        SENTINEL,
                        new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = advice.exception(exception, webRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isInstanceOfSatisfying(
                BaseResponse.class,
                body -> {
                    assertThat(body.getCode()).isEqualTo("COMMON400");
                    assertThat(body.getMessage()).isEqualTo("잘못된 요청입니다.");
                    assertThat(body.getResult()).isNull();
                });
        assertThat(output.getAll()).doesNotContain(SENTINEL);
    }

    @Test
    void explicitDomainErrorStatusMessageRemainsStable() {
        ResponseEntity<?> response = advice.onThrowException(
                new GeneralException(ErrorStatus._BLOG_POST_NOT_FOUND),
                new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isInstanceOfSatisfying(
                BaseResponse.class,
                body -> {
                    assertThat(body.getCode()).isEqualTo("BLOG_4004");
                    assertThat(body.getMessage()).isEqualTo("게시글을 찾을 수 없습니다.");
                    assertThat(body.getResult()).isNull();
                });
    }

    private ServletWebRequest webRequest() {
        return new ServletWebRequest(new MockHttpServletRequest());
    }
}
