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

    private static final String ONE_CLICK_PREFIX =
            "/api/newsletter/one-click-unsubscribe/";
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
        if (event == null) {
            return;
        }
        sanitizeRequest(event.getRequest());
        event.setUser(null);
        event.setBreadcrumbs(null);
        event.setExtras(null);
        event.setThrowable(null);
        event.setMessage(null);
        if (event.getExceptions() != null) {
            event.getExceptions().forEach(exception -> exception.setValue(null));
        }
    }

    static void sanitize(SentryTransaction transaction) {
        if (transaction == null) {
            return;
        }
        sanitizeRequest(transaction.getRequest());
        transaction.setUser(null);
        transaction.setBreadcrumbs(null);
        transaction.setExtras(null);
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
        if (request == null) {
            return;
        }
        request.setData(null);
        request.setQueryString(null);
        request.setCookies(null);
        request.setUrl(sanitizeUrl(request.getUrl()));
        request.setHeaders(removeSensitiveHeaders(request.getHeaders()));
        request.setEnvs(removeSensitiveEnvs(request.getEnvs()));
    }

    private static Map<String, String> removeSensitiveHeaders(
            Map<String, String> source) {
        if (source == null) {
            return null;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !isSensitiveHeader(key)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private static Map<String, String> removeSensitiveEnvs(
            Map<String, String> source) {
        if (source == null) {
            return null;
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !isSensitiveEnv(key)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private static boolean isSensitiveHeader(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_HEADERS.contains(normalized)
                || normalized.contains("forwarded")
                || normalized.startsWith("proxy-")
                || normalized.endsWith("-ip")
                || normalized.equals("client-ip")
                || normalized.equals("remote-addr")
                || normalized.equals("via")
                || normalized.equals("x-envoy-external-address");
    }

    private static boolean isSensitiveEnv(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_ENVS.contains(normalized)
                || normalized.contains("forwarded")
                || normalized.contains("remote_addr")
                || normalized.contains("remote_host")
                || normalized.contains("client_ip")
                || normalized.contains("authorization")
                || normalized.contains("cookie")
                || normalized.contains("internal_api_key");
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
