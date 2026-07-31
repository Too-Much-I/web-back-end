package web.tosunsaeng.domain.newsletter.domain.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import web.tosunsaeng.domain.blog.domain.entity.BlogPost;
import web.tosunsaeng.domain.newsletter.config.NewsletterDeliveryProperties;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class NewsletterEmailTemplateRenderer {

    private final NewsletterDeliveryProperties properties;

    public RenderedEmail render(
            BlogPost post,
            String postUrl,
            String manualUnsubscribeUrl) {
        return renderInternal(post, postUrl, manualUnsubscribeUrl, false);
    }

    public RenderedEmail renderTest(BlogPost post, String postUrl) {
        return renderInternal(post, postUrl, null, true);
    }

    private RenderedEmail renderInternal(
            BlogPost post,
            String postUrl,
            String manualUnsubscribeUrl,
            boolean test) {
        Objects.requireNonNull(post);
        rejectHeaderInjection(post.getTitle());
        String title = Objects.requireNonNullElse(post.getTitle(), "");
        String summary = Objects.requireNonNullElse(post.getSummary(), "");
        String fromName = Objects.requireNonNullElse(properties.getFromName(), "토선생");
        String subject = (test ? "[테스트] " : "") + "[토선생] " + title;

        StringBuilder html = new StringBuilder()
                .append("<p>새 글이 발행되었습니다.</p>")
                .append("<h1>").append(HtmlUtils.htmlEscape(title)).append("</h1>")
                .append("<p>").append(HtmlUtils.htmlEscape(summary)).append("</p>")
                .append("<p><a href=\"")
                .append(HtmlUtils.htmlEscape(postUrl))
                .append("\">글 읽으러 가기</a></p>");
        if (manualUnsubscribeUrl != null) {
            html.append("<p><a href=\"")
                    .append(HtmlUtils.htmlEscape(manualUnsubscribeUrl))
                    .append("\">구독 해지</a></p>");
        }
        html.append("<p>").append(HtmlUtils.htmlEscape(fromName)).append("</p>");

        StringBuilder text = new StringBuilder()
                .append("새 글이 발행되었습니다.\n\n")
                .append(title).append("\n\n")
                .append(summary).append("\n\n")
                .append("글 읽으러 가기: ").append(postUrl).append("\n");
        if (manualUnsubscribeUrl != null) {
            text.append("구독 해지: ").append(manualUnsubscribeUrl).append("\n");
        }
        text.append(fromName);
        return new RenderedEmail(subject, html.toString(), text.toString());
    }

    private void rejectHeaderInjection(String value) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("email 제목에 줄바꿈을 사용할 수 없습니다.");
        }
    }

    public record RenderedEmail(String subject, String htmlBody, String plainTextBody) {
    }
}
