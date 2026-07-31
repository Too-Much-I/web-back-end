package web.tosunsaeng.domain.newsletter.domain.policy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.blog.domain.enums.BlogPostStatus;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;
import web.tosunsaeng.domain.newsletter.domain.sender.NewsletterEmailMessage;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NewsletterDeliveryPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-31T08:00:00Z");

    private NewsletterDeliveryProperties properties;
    private NewsletterLinkBuilder linkBuilder;
    private NewsletterEmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        properties = new NewsletterDeliveryProperties();
        properties.setPublicBaseUrl("https://www.example.test");
        properties.setApiBaseUrl("https://api.example.test");
        properties.setFromName("토선생 서비스");
        linkBuilder = new NewsletterLinkBuilder(properties);
        renderer = new NewsletterEmailTemplateRenderer(properties);
    }

    @Test
    void retryPolicyUsesFiveThirtyAndTwoHourDelaysThenStops() {
        NewsletterRetryPolicy policy = new NewsletterRetryPolicy();

        assertThat(policy.nextRetryAt(1, NOW)).contains(NOW.plusSeconds(5 * 60));
        assertThat(policy.nextRetryAt(2, NOW)).contains(NOW.plusSeconds(30 * 60));
        assertThat(policy.nextRetryAt(3, NOW)).contains(NOW.plusSeconds(2 * 60 * 60));
        assertThat(policy.nextRetryAt(4, NOW)).isEmpty();
        assertThat(NewsletterRetryPolicy.MAX_PROVIDER_ATTEMPTS).isEqualTo(4);
    }

    @Test
    void claimTokensUseThirtyTwoRandomBytesAndUrlSafeEncoding() {
        NewsletterClaimTokenGenerator generator = new NewsletterClaimTokenGenerator();

        String first = generator.create();
        String second = generator.create();

        assertThat(first)
                .hasSize(43)
                .matches("^[A-Za-z0-9_-]+$")
                .isNotEqualTo(second);
        assertThat(second).hasSize(43).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void postAndManualUnsubscribeLinksUseOnlyPublicOrigin() {
        String postUrl = linkBuilder.postUrl("post-slug");
        String manualUrl = linkBuilder.manualUnsubscribeUrl("opaque.signed_token");

        assertThat(postUrl)
                .startsWith("https://www.example.test/blog/post-slug?")
                .contains("utm_source=newsletter")
                .contains("utm_medium=email")
                .contains("utm_campaign=post_notification")
                .doesNotContain("api.example.test");
        assertThat(manualUrl)
                .isEqualTo("https://www.example.test/newsletter/unsubscribe"
                        + "?token=opaque.signed_token")
                .doesNotContain("api.example.test");
    }

    @Test
    void oneClickHeadersUseOnlyApiOriginAndExactRfcValues() {
        Map<String, String> headers = linkBuilder.oneClickHeaders("opaque.token");

        assertThat(headers).containsExactlyInAnyOrderEntriesOf(Map.of(
                NewsletterLinkBuilder.LIST_UNSUBSCRIBE,
                "<https://api.example.test/api/newsletter/one-click-unsubscribe/opaque.token>",
                NewsletterLinkBuilder.LIST_UNSUBSCRIBE_POST,
                "List-Unsubscribe=One-Click"));
        assertThat(headers.values()).allMatch(value ->
                !value.contains("www.example.test"));
    }

    @Test
    void productionTemplateHasHtmlPlainSummaryLinksAndNoFullMarkdown() {
        BlogPost post = post(
                "제목 <script>alert(1)</script>",
                "요약 & 안내",
                "SECRET FULL MARKDOWN");

        NewsletterEmailTemplateRenderer.RenderedEmail email = renderer.render(
                post,
                "https://www.example.test/blog/post",
                "https://www.example.test/newsletter/unsubscribe?token=redacted");

        assertThat(email.subject()).isEqualTo(
                "[토선생] 제목 <script>alert(1)</script>");
        assertThat(email.htmlBody())
                .contains("제목 &lt;script&gt;alert(1)&lt;/script&gt;")
                .contains("요약 &amp; 안내")
                .contains("글 읽으러 가기")
                .contains("구독 해지")
                .contains("토선생 서비스")
                .doesNotContain("SECRET FULL MARKDOWN")
                .doesNotContain("<script>");
        assertThat(email.plainTextBody())
                .contains("새 글이 발행되었습니다.")
                .contains("요약 & 안내")
                .contains("글 읽으러 가기:")
                .contains("구독 해지:")
                .doesNotContain("SECRET FULL MARKDOWN");
    }

    @Test
    void testTemplateHasMarkerAndNoStateChangingUnsubscribeLink() {
        NewsletterEmailTemplateRenderer.RenderedEmail email = renderer.renderTest(
                post("제목", "요약", "본문"),
                "https://www.example.test/blog/post");

        assertThat(email.subject()).isEqualTo("[테스트] [토선생] 제목");
        assertThat(email.htmlBody()).doesNotContain("구독 해지", "token=");
        assertThat(email.plainTextBody()).doesNotContain("구독 해지", "token=");
    }

    @Test
    void subjectHeaderInjectionAndMissingLinkValuesAreRejected() {
        assertThatThrownBy(() -> renderer.render(
                post("제목\r\nBcc: victim@example.test", "요약", "본문"),
                "https://www.example.test/blog/post",
                "https://www.example.test/unsubscribe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("줄바꿈");
        assertThatThrownBy(() -> linkBuilder.oneClickHeaders(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("token=");
    }

    @Test
    void emailMessageStringRepresentationRedactsEverySensitiveField() {
        NewsletterEmailMessage message = new NewsletterEmailMessage(
                "user@example.test",
                "sensitive subject",
                "<p>sensitive html token-value</p>",
                "sensitive plain token-value",
                Map.of("List-Unsubscribe", "<https://api/token-value>"));

        assertThat(message.toString())
                .isEqualTo("NewsletterEmailMessage[redacted]")
                .doesNotContain("user@example.test", "token-value", "sensitive");
    }

    private BlogPost post(String title, String summary, String contentMarkdown) {
        return BlogPost.builder()
                .id("post-id")
                .slug("post-slug")
                .title(title)
                .summary(summary)
                .contentMarkdown(contentMarkdown)
                .status(BlogPostStatus.PUBLISHED)
                .publishedAt(NOW.minusSeconds(60))
                .newsletterEnabled(true)
                .createdAt(NOW.minusSeconds(100))
                .updatedAt(NOW)
                .build();
    }
}
