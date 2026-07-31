package web.tosunsaeng.domain.newsletter.domain.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;
import web.tosunsaeng.domain.newsletter.domain.enums.NewsletterRateLimitScope;
import web.tosunsaeng.domain.newsletter.domain.policy.NewsletterRateLimitKeyFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RedisNewsletterRateLimitRepository implements NewsletterRateLimitRepository {

    private static final String ALLOWED = "ALLOWED";
    private static final String DENIED = "DENIED";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> rateLimitScript;
    private final NewsletterRateLimitProperties properties;

    public RedisNewsletterRateLimitRepository(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("newsletterSubscribeRateLimitScript") RedisScript<List> rateLimitScript,
            NewsletterRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
        this.properties = properties;
    }

    @Override
    public AdmissionResult admit(NewsletterRateLimitKeyFactory.RateLimitKeys keys) {
        List<?> rawResult = redisTemplate.execute(
                rateLimitScript,
                keys.asList(),
                Integer.toString(properties.getMediumLimit()),
                Long.toString(properties.getMediumWindowSeconds()),
                Integer.toString(properties.getDailyLimit()),
                Long.toString(properties.getDailyWindowSeconds()));
        return decode(rawResult);
    }

    private AdmissionResult decode(List<?> rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            throw new IllegalStateException("Redis newsletter rate limit 결과가 없습니다.");
        }
        String status = asString(rawResult.getFirst());
        if (ALLOWED.equals(status)) {
            if (rawResult.size() != 1) {
                throw new IllegalStateException("Redis newsletter 허용 결과 형식이 올바르지 않습니다.");
            }
            return AdmissionResult.allowedResult();
        }
        if (!DENIED.equals(status)
                || rawResult.size() < 3
                || rawResult.size() % 2 == 0) {
            throw new IllegalStateException("Redis newsletter 차단 결과 형식이 올바르지 않습니다.");
        }

        List<Blocker> blockers = new ArrayList<>();
        for (int index = 1; index < rawResult.size(); index += 2) {
            NewsletterRateLimitScope scope;
            long ttl;
            try {
                scope = NewsletterRateLimitScope.valueOf(asString(rawResult.get(index)));
                ttl = Long.parseLong(asString(rawResult.get(index + 1)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Redis newsletter blocker 결과 형식이 올바르지 않습니다.");
            }
            if (ttl < 0) {
                throw new IllegalStateException("Redis newsletter blocker TTL이 올바르지 않습니다.");
            }
            blockers.add(new Blocker(scope, ttl));
        }
        return AdmissionResult.denied(blockers);
    }

    private String asString(Object value) {
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        throw new IllegalStateException("Redis newsletter script 결과 타입이 올바르지 않습니다.");
    }
}
