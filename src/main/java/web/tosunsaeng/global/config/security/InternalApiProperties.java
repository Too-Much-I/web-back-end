package web.tosunsaeng.global.config.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@Getter
@Setter
@ConfigurationProperties(prefix = "internal.api")
public class InternalApiProperties implements InitializingBean {

    static final int MIN_KEY_BYTES = 32;
    static final int MAX_KEY_BYTES = 1_024;

    private boolean enabled = false;
    private String key = "";

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        if (key == null || key.isBlank() || containsLineBreak(key)) {
            throw invalidConfiguration();
        }
        int keyBytes = key.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytes < MIN_KEY_BYTES || keyBytes > MAX_KEY_BYTES) {
            throw invalidConfiguration();
        }
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    private IllegalStateException invalidConfiguration() {
        return new IllegalStateException("INTERNAL_API_KEY 설정이 올바르지 않습니다.");
    }
}
