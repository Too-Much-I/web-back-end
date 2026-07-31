package web.tosunsaeng.domain.newsletter.domain.policy;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.config.NewsletterRateLimitProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

@Component
public class NewsletterRateLimitHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] IP_DOMAIN =
            "newsletter-subscribe-ip".getBytes(StandardCharsets.US_ASCII);

    private final byte[] secret;

    public NewsletterRateLimitHasher(NewsletterRateLimitProperties properties) {
        this.secret = properties.secretBytes().clone();
    }

    public String hashIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("client IP는 비어 있을 수 없습니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            mac.update(IP_DOMAIN);
            mac.update((byte) 0);
            byte[] digest = mac.doFinal(clientIp.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("newsletter rate limit hash를 생성할 수 없습니다.");
        }
    }
}
