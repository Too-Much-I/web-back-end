package web.tosunsaeng.domain.newsletter.config;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.protocol.Request;
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
        assertThat(event.getUser().getIpAddress()).isNull();
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
        assertThat(transaction.getUser().getEmail()).isNull();
        assertThat(transaction.getUser().getIpAddress()).isNull();
    }

    @Test
    void leavesOtherEndpointsAndNewsletterGetRequestsUntouched() {
        SentryEvent other = new SentryEvent();
        other.setRequest(sensitiveRequest("https://api.example.test/api/posts"));
        SentryEvent get = new SentryEvent();
        Request getRequest = sensitiveRequest(
                "https://api.example.test/api/newsletter/unsubscribe?token=sensitive");
        getRequest.setMethod("GET");
        get.setRequest(getRequest);

        NewsletterTelemetryPrivacyConfig.sanitize(other);
        NewsletterTelemetryPrivacyConfig.sanitize(get);

        assertThat(other.getRequest().getData()).isNotNull();
        assertThat(other.getRequest().getQueryString()).isNotNull();
        assertThat(get.getRequest().getData()).isNotNull();
        assertThat(get.getRequest().getQueryString()).isNotNull();
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

    private Request sensitiveRequest(String url) {
        Request request = new Request();
        request.setMethod("POST");
        request.setUrl(url);
        request.setQueryString("token=sensitive-token&email=user@example.com");
        request.setData(Map.of(
                "email", "user@example.com",
                "token", "sensitive-token"));
        request.setCookies("anon_session=sensitive-cookie-token");
        request.setHeaders(Map.of(
                "Content-Type", "application/json",
                "Cookie", "anon_session=sensitive-cookie-token",
                "Authorization", "Bearer sensitive-token",
                "X-Forwarded-For", "203.0.113.10"));
        request.setEnvs(Map.of(
                "REMOTE_ADDR", "203.0.113.10",
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
                        "X-Forwarded-For");
        assertThat(request.getEnvs())
                .containsEntry("SAFE_VALUE", "kept")
                .doesNotContainKey("REMOTE_ADDR");
    }
}
