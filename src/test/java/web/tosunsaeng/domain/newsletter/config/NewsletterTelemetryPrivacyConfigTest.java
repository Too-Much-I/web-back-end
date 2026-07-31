package web.tosunsaeng.domain.newsletter.config;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryException;
import io.sentry.protocol.SentryTransaction;
import io.sentry.protocol.TransactionInfo;
import io.sentry.protocol.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NewsletterTelemetryPrivacyConfigTest {

    @Test
    void redactsSubscribeEmailTokenCookieAndIpFromSentryEvent() {
        SentryEvent event = new SentryEvent();
        event.setRequest(sensitiveRequest(
                "https://api.example.test/api/newsletter/subscribe?email=user@example.com"));
        User user = new User();
        user.setIpAddress("203.0.113.10");
        event.setUser(user);

        new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendCallback()
                .execute(event, new Hint());

        assertSanitized(event.getRequest());
        assertThat(event.getUser()).isNull();
        assertThat(event.getRequest().getUrl())
                .isEqualTo("https://api.example.test/api/newsletter/subscribe");
    }

    @Test
    void redactsUnsubscribeTransactionRequestDataAndQuery() {
        SentryTransaction transaction = new SentryTransaction(
                "POST /api/newsletter/unsubscribe",
                1.0,
                2.0,
                List.of(),
                Map.of(),
                null,
                new TransactionInfo("custom"));
        transaction.setRequest(sensitiveRequest(
                "https://api.example.test/api/newsletter/unsubscribe?token=sensitive"));
        User user = new User();
        user.setEmail("user@example.com");
        user.setIpAddress("203.0.113.10");
        transaction.setUser(user);

        new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendTransactionCallback()
                .execute(transaction, new Hint());

        assertSanitized(transaction.getRequest());
        assertThat(transaction.getRequest().getUrl())
                .isEqualTo("https://api.example.test/api/newsletter/unsubscribe");
        assertThat(transaction.getUser()).isNull();
    }

    @Test
    void sanitizesRequestDataForEveryEndpointAndMethod() {
        SentryEvent other = new SentryEvent();
        other.setRequest(sensitiveRequest("https://api.example.test/api/posts"));
        SentryEvent get = new SentryEvent();
        Request getRequest = sensitiveRequest(
                "https://api.example.test/api/newsletter/unsubscribe?token=sensitive");
        getRequest.setMethod("GET");
        get.setRequest(getRequest);

        NewsletterTelemetryPrivacyConfig.sanitize(other);
        NewsletterTelemetryPrivacyConfig.sanitize(get);

        assertSanitized(other.getRequest());
        assertSanitized(get.getRequest());
    }

    @Test
    void removesExceptionMessagesBreadcrumbsExtrasAndUserPii() {
        String sentinel = "sensitive-user@example.test-token-comment";
        SentryEvent event = new SentryEvent(new IllegalStateException(sentinel));
        SentryException exception = new SentryException();
        exception.setType(IllegalStateException.class.getName());
        exception.setValue(sentinel);
        event.setExceptions(List.of(exception));
        Message message = new Message();
        message.setFormatted(sentinel);
        event.setMessage(message);
        event.setExtra("requestBody", sentinel);
        event.addBreadcrumb(sentinel);
        User user = new User();
        user.setEmail(sentinel);
        event.setUser(user);

        NewsletterTelemetryPrivacyConfig.sanitize(event);

        assertThat(event.getMessage()).isNull();
        assertThat(event.getThrowable()).isNull();
        assertThat(event.getExceptions())
                .singleElement()
                .extracting(SentryException::getValue)
                .isNull();
        assertThat(event.getBreadcrumbs()).isNull();
        assertThat(event.getExtras()).isNull();
        assertThat(event.getUser()).isNull();
    }

    @Test
    void redactsOneClickPathTokenFromSentryEvent() {
        SentryEvent event = new SentryEvent();
        event.setRequest(sensitiveRequest(
                "https://api.example.test/api/newsletter/one-click-unsubscribe/signed-sensitive-token"));

        new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendCallback()
                .execute(event, new Hint());

        assertSanitized(event.getRequest());
        assertThat(event.getRequest().getUrl())
                .isEqualTo("https://api.example.test/api/newsletter/one-click-unsubscribe/{token}")
                .doesNotContain("signed-sensitive-token");
    }

    @Test
    void dropsOneClickTransactionBecauseSdkCannotSafelyRenameIt() {
        SentryTransaction transaction = new SentryTransaction(
                "POST /api/newsletter/one-click-unsubscribe/signed-sensitive-token",
                1.0,
                2.0,
                List.of(),
                Map.of(),
                null,
                new TransactionInfo("custom"));
        transaction.setRequest(sensitiveRequest(
                "https://api.example.test/api/newsletter/one-click-unsubscribe/signed-sensitive-token"));

        SentryTransaction result = new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendTransactionCallback()
                .execute(transaction, new Hint());

        assertThat(result).isNull();
    }

    @Test
    void redactsInternalApiKeyEmailCommentBodyAndProxyDataFromEvent() {
        SentryEvent event = new SentryEvent();
        event.setRequest(sensitiveRequest(
                "https://api.example.test/internal/newsletter/posts/post-id/test?email=user@example.com"));
        User user = new User();
        user.setId("operator-id");
        user.setEmail("user@example.com");
        user.setUsername("operator");
        user.setIpAddress("203.0.113.10");
        event.setUser(user);

        new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendCallback()
                .execute(event, new Hint());

        assertSanitized(event.getRequest());
        assertThat(event.getRequest().getUrl())
                .isEqualTo("https://api.example.test/internal/newsletter/posts/post-id/test");
        assertThat(event.getRequest().getHeaders())
                .doesNotContainKeys(
                        "X-Internal-Api-Key",
                        "Forwarded",
                        "X-Forwarded-Host",
                        "X-Forwarded-Proto");
        assertThat(event.getUser()).isNull();
    }

    @Test
    void sanitizesButDoesNotDropInternalTransaction() {
        SentryTransaction transaction = new SentryTransaction(
                "PATCH /internal/comments/{commentId}/hide",
                1.0,
                2.0,
                List.of(),
                Map.of(),
                null,
                new TransactionInfo("custom"));
        transaction.setRequest(sensitiveRequest(
                "https://api.example.test/internal/comments/comment-id/hide"));

        SentryTransaction result = new NewsletterTelemetryPrivacyConfig()
                .newsletterBeforeSendTransactionCallback()
                .execute(transaction, new Hint());

        assertThat(result).isSameAs(transaction);
        assertSanitized(result.getRequest());
    }

    private Request sensitiveRequest(String url) {
        Request request = new Request();
        request.setMethod("POST");
        request.setUrl(url);
        request.setQueryString("token=sensitive-token&email=user@example.com");
        request.setData(Map.of(
                "email", "user@example.com",
                "token", "sensitive-token",
                "content", "sensitive comment body"));
        request.setCookies("anon_session=sensitive-cookie-token");
        request.setHeaders(Map.of(
                "Content-Type", "application/json",
                "Cookie", "anon_session=sensitive-cookie-token",
                "Authorization", "Bearer sensitive-token",
                "X-Internal-Api-Key", "sensitive-internal-key",
                "X-Forwarded-For", "203.0.113.10",
                "Forwarded", "for=203.0.113.10",
                "X-Forwarded-Host", "proxy.example.test",
                "X-Forwarded-Proto", "https"));
        request.setEnvs(Map.of(
                "REMOTE_ADDR", "203.0.113.10",
                "HTTP_X_INTERNAL_API_KEY", "sensitive-internal-key",
                "HTTP_X_FORWARDED_HOST", "proxy.example.test",
                "SAFE_VALUE", "kept"));
        return request;
    }

    private void assertSanitized(Request request) {
        assertThat(request.getData()).isNull();
        assertThat(request.getQueryString()).isNull();
        assertThat(request.getCookies()).isNull();
        assertThat(request.getHeaders())
                .containsEntry("Content-Type", "application/json")
                .doesNotContainKeys(
                        "Cookie",
                        "Authorization",
                        "X-Internal-Api-Key",
                        "X-Forwarded-For",
                        "Forwarded",
                        "X-Forwarded-Host",
                        "X-Forwarded-Proto");
        assertThat(request.getEnvs())
                .containsEntry("SAFE_VALUE", "kept")
                .doesNotContainKeys(
                        "REMOTE_ADDR",
                        "HTTP_X_INTERNAL_API_KEY",
                        "HTTP_X_FORWARDED_HOST");
    }
}
