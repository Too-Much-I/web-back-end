package web.tosunsaeng.domain.newsletter.domain.policy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class NewsletterRateLimitKeyFactory {

    static final String PREFIX = "newsletter:subscribe:rate:ip:";
    private static final Pattern SAFE_SEGMENT = Pattern.compile("^[A-Za-z0-9_-]+$");

    public RateLimitKeys create(String ipHash) {
        if (ipHash == null || !SAFE_SEGMENT.matcher(ipHash).matches()) {
            throw new IllegalArgumentException("IP hash 형식이 올바르지 않습니다.");
        }
        return new RateLimitKeys(
                PREFIX + "medium:" + ipHash,
                PREFIX + "daily:" + ipHash);
    }

    public record RateLimitKeys(String medium, String daily) {

        public List<String> asList() {
            return List.of(medium, daily);
        }
    }
}
