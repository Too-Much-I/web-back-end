package web.tosunsaeng.domain.comment.domain.policy;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.comment.config.BlogCommentAbuseProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Component
public class CommentRateLimitHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] IP_DOMAIN = "client-ip"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CONTENT_DOMAIN = "duplicate-content"
            .getBytes(StandardCharsets.US_ASCII);

    private final byte[] secret;

    public CommentRateLimitHasher(BlogCommentAbuseProperties properties) {
        this.secret = properties.secretBytes().clone();
    }

    public String hashIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("client IP는 비어 있을 수 없습니다.");
        }
        return hash(IP_DOMAIN, clientIp);
    }

    public String hashContent(String normalizedContent) {
        if (normalizedContent == null) {
            throw new IllegalArgumentException("normalized content는 null일 수 없습니다.");
        }
        return hash(CONTENT_DOMAIN, normalizedContent);
    }

    private String hash(byte[] domain, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(domain);
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("댓글 rate limit hash를 생성할 수 없습니다.", exception);
        }
    }
}
