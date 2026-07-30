package web.tosunsaeng.domain.blog.comment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
@ConfigurationProperties(prefix = "blog.comment.abuse")
public class BlogCommentAbuseProperties implements InitializingBean {

    static final int MIN_SECRET_BYTES = 32;

    private String secret = "";
    private boolean enabled = true;
    private long duplicateTtlSeconds = 600;
    private int visitorShortLimit = 1;
    private long visitorShortWindowSeconds = 10;
    private int visitorMediumLimit = 5;
    private long visitorMediumWindowSeconds = 600;
    private int visitorDailyLimit = 20;
    private long visitorDailyWindowSeconds = 86_400;
    private int ipMediumLimit = 10;
    private long ipMediumWindowSeconds = 600;
    private int ipDailyLimit = 50;
    private long ipDailyWindowSeconds = 86_400;

    @Override
    public void afterPropertiesSet() {
        if (enabled && (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES)) {
            throw new IllegalStateException(
                    "BLOG_COMMENT_RATE_LIMIT_SECRET은 UTF-8 기준 32 byte 이상이어야 합니다.");
        }

        requirePositive(duplicateTtlSeconds, "duplicate TTL");
        requirePositive(visitorShortLimit, "visitor short limit");
        requirePositive(visitorShortWindowSeconds, "visitor short window");
        requirePositive(visitorMediumLimit, "visitor medium limit");
        requirePositive(visitorMediumWindowSeconds, "visitor medium window");
        requirePositive(visitorDailyLimit, "visitor daily limit");
        requirePositive(visitorDailyWindowSeconds, "visitor daily window");
        requirePositive(ipMediumLimit, "IP medium limit");
        requirePositive(ipMediumWindowSeconds, "IP medium window");
        requirePositive(ipDailyLimit, "IP daily limit");
        requirePositive(ipDailyWindowSeconds, "IP daily window");

        if (!(visitorShortLimit < visitorMediumLimit
                && visitorMediumLimit < visitorDailyLimit)) {
            throw new IllegalStateException(
                    "visitor limit은 short, medium, daily 순으로 증가해야 합니다.");
        }
        if (!(visitorShortWindowSeconds < visitorMediumWindowSeconds
                && visitorMediumWindowSeconds < visitorDailyWindowSeconds)) {
            throw new IllegalStateException(
                    "visitor window는 short, medium, daily 순으로 증가해야 합니다.");
        }
        if (ipMediumLimit >= ipDailyLimit
                || ipMediumWindowSeconds >= ipDailyWindowSeconds) {
            throw new IllegalStateException(
                    "IP limit과 window는 medium보다 daily가 커야 합니다.");
        }
    }

    public byte[] secretBytes() {
        return secret == null
                ? new byte[0]
                : secret.getBytes(StandardCharsets.UTF_8);
    }

    private void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalStateException(name + "은 양수여야 합니다.");
        }
    }
}
