package web.tosunsaeng.domain.blog.comment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "blog.anonymous")
public class AnonymousSessionProperties implements InitializingBean {

    static final int MIN_TOKEN_SECRET_BYTES = 32;

    private String tokenSecret = "";
    private boolean cookieSecure = true;
    private long cookieMaxAgeDays = 180;

    @Override
    public void afterPropertiesSet() {
        if (tokenSecret == null
                || tokenSecret.getBytes(StandardCharsets.UTF_8).length < MIN_TOKEN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_TOKEN_SECRET은 UTF-8 기준 32 byte 이상이어야 합니다.");
        }
        if (cookieMaxAgeDays <= 0) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS는 양수여야 합니다.");
        }
        try {
            Duration.ofDays(cookieMaxAgeDays);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "BLOG_ANONYMOUS_COOKIE_MAX_AGE_DAYS가 허용 범위를 초과했습니다.",
                    exception);
        }
    }

    public byte[] tokenSecretBytes() {
        return tokenSecret.getBytes(StandardCharsets.UTF_8);
    }
}
