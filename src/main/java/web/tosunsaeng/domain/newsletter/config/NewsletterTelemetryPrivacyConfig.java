package web.tosunsaeng.domain.newsletter.config;

import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import io.sentry.protocol.SentryTransaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class NewsletterTelemetryPrivacyConfig {

    private static final Set<String> SENSITIVE_PATHS = Set.of(
            "/api/newsletter/subscribe",
            "/api/newsletter/unsubscribe");
    private static final String ONE_CLICK_PREFIX =
            "/api/newsletter/one-click-unsubscribe/";
    private static final String INTERNAL_PREFIX = "/internal/";
    private static final String REDACTED_ONE_CLICK_PATH =
            "/api/newsletter/one-click-unsubscribe/{token}";
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "x-internal-api-key",
            "authorization",
            "cookie",
            "forwarded",
            "x-forwarded-for",
            "x-forwarded-host",
            "x-forwarded-proto",
            "x-forwarded-port",
            "x-real-ip",
            "cf-connecting-ip",
            "true-client-ip",
            "x-client-ip",
            "x-cluster-client-ip");
    private static final Set<String> SENSITIVE_ENVS = Set.of(
            "remote_addr",
            "remote_host",
            "http_x_internal_api_key",
            "http_authorization",
            "http_cookie",
            "http_forwarded",
            "http_x_forwarded_for",
            "http_x_forwarded_host",
            "http_x_forwarded_proto",
            "http_x_forwarded_port",
            "http_x_real_ip",
            "http_cf_connecting_ip",
            "http_true_client_ip",
            "http_x_client_ip",
            "http_x_cluster_client_ip");

    @Bean
    public SentryOptions.BeforeSendCallback newsletterBeforeSendCallback() {
        return (event, hint) -> {
            sanitize(event);
            return event;
        };
    }

    @Bean
    public SentryOptions.BeforeSendTransactionCallback
            newsletterBeforeSendTransactionCallback() {
        return (transaction, hint) -> {
            if (transaction != null && isOneClick(transaction.getRequest())) {
                return null;
            }
            sanitize(transaction);
            return transaction;
        };
    }

    static void sanitize(SentryEvent event) {
        if (event == null || !isSensitiveRequest(event.getRequest())) {
            return;
        }
        sanitizeRequest(event.getRequest());
        event.setUser(null);
    }

    static void sanitize(SentryTransaction transaction) {
        if (transaction == null
                || !isSensitiveRequest(transaction.getRequest())) {
            return;
        }
        sanitizeRequest(transaction.getRequest());
        transaction.setUser(null);
    }

    private static boolean isSensitiveRequest(Request request) {
        if (request == null) {
            return false;
        }
        String path = extractPath(request.getUrl());
        return path != null && (path.startsWith(INTERNAL_PREFIX)
                || path.startsWith(ONE_CLICK_PREFIX)
                || ("POST".equalsIgnoreCase(request.getMethod())
                && SENSITIVE_PATHS.contains(path)));
    }

    private static String extractPath(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(url);
            return uri.getPath();
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private static void sanitizeRequest(Request request) {
        request.setData(null);
        request.setQueryString(null);
        request.setCookies(null);
        request.setUrl(sanitizeUrl(request.getUrl()));
        request.setHeaders(removeSensitiveEntries(
                request.getHeaders(),
                SENSITIVE_HEADERS));
        request.setEnvs(removeSensitiveEntries(
                request.getEnvs(),
                SENSITIVE_ENVS));
    }

    private static Map<String, String> removeSensitiveEntries(
            Map<String, String> source,
            Set<String> sensitiveNames) {
        if (source == null) {
            return null;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null
                    && !sensitiveNames.contains(key.toLowerCase(Locale.ROOT))) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private static String withoutQueryAndFragment(String url) {
        if (url == null) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        int endIndex = url.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (fragmentIndex >= 0) {
            endIndex = Math.min(endIndex, fragmentIndex);
        }
        return url.substring(0, endIndex);
    }

    private static boolean isOneClick(Request request) {
        String path = request == null ? null : extractPath(request.getUrl());
        return path != null && path.startsWith(ONE_CLICK_PREFIX);
    }

    private static String sanitizeUrl(String url) {
        String withoutQuery = withoutQueryAndFragment(url);
        if (withoutQuery == null) {
            return null;
        }
        int prefixIndex = withoutQuery.indexOf(ONE_CLICK_PREFIX);
        if (prefixIndex < 0) {
            return withoutQuery;
        }
        return withoutQuery.substring(0, prefixIndex) + REDACTED_ONE_CLICK_PATH;
    }
}
