package web.tosunsaeng.domain.newsletter.domain.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class NewsletterLinkBuilder {

    public static final String LIST_UNSUBSCRIBE = "List-Unsubscribe";
    public static final String LIST_UNSUBSCRIBE_POST = "List-Unsubscribe-Post";
    public static final String ONE_CLICK_FORM_FIELD_VALUE = "One-Click";
    public static final String ONE_CLICK_FORM_VALUE = "List-Unsubscribe=One-Click";

    private final NewsletterDeliveryProperties properties;

    public String postUrl(String slug) {
        return UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
                .pathSegment("blog", requireValue(slug, "slug"))
                .queryParam("utm_source", "newsletter")
                .queryParam("utm_medium", "email")
                .queryParam("utm_campaign", "post_notification")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public String manualUnsubscribeUrl(String token) {
        return UriComponentsBuilder.fromUriString(properties.getPublicBaseUrl())
                .pathSegment("newsletter", "unsubscribe")
                .queryParam("token", requireValue(token, "unsubscribe token"))
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public String oneClickUrl(String token) {
        return UriComponentsBuilder.fromUriString(properties.getApiBaseUrl())
                .pathSegment(
                        "api",
                        "newsletter",
                        "one-click-unsubscribe",
                        requireValue(token, "unsubscribe token"))
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public Map<String, String> oneClickHeaders(String token) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(LIST_UNSUBSCRIBE, "<" + oneClickUrl(token) + ">");
        headers.put(LIST_UNSUBSCRIBE_POST, ONE_CLICK_FORM_VALUE);
        return Map.copyOf(headers);
    }

    private String requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "이 필요합니다.");
        }
        return value;
    }
}
