package web.tosunsaeng.domain.newsletter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
@ConfigurationProperties(prefix = "newsletter.subscribe-rate-limit")
public class NewsletterRateLimitProperties implements InitializingBean {

    static final int MIN_SECRET_BYTES = 32;

    private String secret = "";
    private boolean enabled = true;
    private int mediumLimit = 10;
    private long mediumWindowSeconds = 600;
    private int dailyLimit = 30;
    private long dailyWindowSeconds = 86_400;

    @Override
    public void afterPropertiesSet() {
        if (enabled && (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES)) {
            throw new IllegalStateException(
                    "NEWSLETTER_SUBSCRIBE_RATE_LIMIT_SECRET은 UTF-8 기준 32 byte 이상이어야 합니다.");
        }
        requirePositive(mediumLimit, "medium limit");
        requirePositive(mediumWindowSeconds, "medium window");
        requirePositive(dailyLimit, "daily limit");
        requirePositive(dailyWindowSeconds, "daily window");
        if (mediumLimit >= dailyLimit || mediumWindowSeconds >= dailyWindowSeconds) {
            throw new IllegalStateException("newsletter rate limit은 medium보다 daily가 커야 합니다.");
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
