package web.tosunsaeng.domain.comment.domain.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;
import web.tosunsaeng.domain.comment.domain.enums.CommentLimitScope;
import web.tosunsaeng.domain.comment.domain.policy.CommentRateLimitKeyFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RedisCommentRateLimitRepository implements CommentRateLimitRepository {

    private static final String ALLOWED = "ALLOWED";
    private static final String DENIED = "DENIED";

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisScript<List> admissionScript;
    private final RedisScript<Long> duplicateReleaseScript;
    private final BlogCommentAbuseProperties properties;

    public RedisCommentRateLimitRepository(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("blogCommentAdmissionScript") RedisScript<List> admissionScript,
            @Qualifier("blogCommentDuplicateReleaseScript")
            RedisScript<Long> duplicateReleaseScript,
            BlogCommentAbuseProperties properties) {
        this.redisTemplate = redisTemplate;
        this.admissionScript = admissionScript;
        this.duplicateReleaseScript = duplicateReleaseScript;
        this.properties = properties;
    }

    @Override
    public AdmissionResult admit(
            CommentRateLimitKeyFactory.RateLimitKeys keys,
            String reservationOwner) {
        List<?> rawResult = redisTemplate.execute(
                admissionScript,
                keys.asList(),
                Integer.toString(properties.getVisitorShortLimit()),
                Long.toString(properties.getVisitorShortWindowSeconds()),
                Integer.toString(properties.getVisitorMediumLimit()),
                Long.toString(properties.getVisitorMediumWindowSeconds()),
                Integer.toString(properties.getVisitorDailyLimit()),
                Long.toString(properties.getVisitorDailyWindowSeconds()),
                Integer.toString(properties.getIpMediumLimit()),
                Long.toString(properties.getIpMediumWindowSeconds()),
                Integer.toString(properties.getIpDailyLimit()),
                Long.toString(properties.getIpDailyWindowSeconds()),
                Long.toString(properties.getDuplicateTtlSeconds()),
                reservationOwner);
        return decode(rawResult);
    }

    @Override
    public boolean releaseDuplicate(String duplicateKey, String reservationOwner) {
        Long released = redisTemplate.execute(
                duplicateReleaseScript,
                List.of(duplicateKey),
                reservationOwner);
        if (released == null) {
            throw new IllegalStateException("Redis duplicate release 결과가 없습니다.");
        }
        if (released != 0L && released != 1L) {
            throw new IllegalStateException("Redis duplicate release 결과가 올바르지 않습니다.");
        }
        return released == 1L;
    }

    private AdmissionResult decode(List<?> rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            throw new IllegalStateException("Redis admission 결과가 없습니다.");
        }
        String status = asString(rawResult.getFirst());
        if (ALLOWED.equals(status)) {
            if (rawResult.size() != 1) {
                throw new IllegalStateException("Redis admission 허용 결과 형식이 올바르지 않습니다.");
            }
            return AdmissionResult.allowedResult();
        }
        if (!DENIED.equals(status)
                || rawResult.size() < 3
                || rawResult.size() % 2 == 0) {
            throw new IllegalStateException("Redis admission 차단 결과 형식이 올바르지 않습니다.");
        }

        List<Blocker> blockers = new ArrayList<>();
        for (int index = 1; index < rawResult.size(); index += 2) {
            CommentLimitScope scope;
            long ttl;
            try {
                scope = CommentLimitScope.valueOf(asString(rawResult.get(index)));
                ttl = Long.parseLong(asString(rawResult.get(index + 1)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "Redis admission blocker 결과 형식이 올바르지 않습니다.", exception);
            }
            if (ttl < 0) {
                throw new IllegalStateException("Redis admission blocker TTL이 올바르지 않습니다.");
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
        throw new IllegalStateException("Redis script 결과 타입이 올바르지 않습니다.");
    }
}
