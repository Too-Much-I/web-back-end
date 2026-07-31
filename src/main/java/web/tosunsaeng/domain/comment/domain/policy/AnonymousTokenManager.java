package web.tosunsaeng.domain.comment.domain.policy;

import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.comment.config.AnonymousSessionProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class AnonymousTokenManager {

    static final int RAW_TOKEN_BYTES = 32;
    private static final int RAW_TOKEN_LENGTH = 43;
    private static final Pattern RAW_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecureRandom secureRandom;
    private final byte[] secret;

    public AnonymousTokenManager(
            SecureRandom secureRandom,
            AnonymousSessionProperties properties) {
        this.secureRandom = secureRandom;
        this.secret = properties.tokenSecretBytes().clone();
    }

    public AnonymousToken createToken() {
        byte[] tokenBytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new AnonymousToken(rawToken, hash(rawToken));
    }

    public boolean isValidRawToken(String rawToken) {
        if (rawToken == null
                || rawToken.length() != RAW_TOKEN_LENGTH
                || !RAW_TOKEN_PATTERN.matcher(rawToken).matches()) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(rawToken).length == RAW_TOKEN_BYTES;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public String hash(String rawToken) {
        if (!isValidRawToken(rawToken)) {
            throw new IllegalArgumentException("유효하지 않은 익명 세션 토큰입니다.");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawToken.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("익명 세션 토큰 hash를 생성할 수 없습니다.", exception);
        }
    }

    public record AnonymousToken(String rawToken, String tokenHash) {
    }
}
