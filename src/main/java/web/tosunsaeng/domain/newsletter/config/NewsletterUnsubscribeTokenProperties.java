package web.tosunsaeng.domain.newsletter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Getter
@Setter
@ConfigurationProperties(prefix = "newsletter.unsubscribe-token")
public class NewsletterUnsubscribeTokenProperties implements InitializingBean {

    static final int MIN_SECRET_BYTES = 32;
    static final Pattern KEY_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private String secret = "";
    private String keyId = "";
    private Map<String, String> previousKeys = new LinkedHashMap<>();
    private int formatVersion = 1;
    private long maxFutureSkewSeconds = 300;
    private int maxTokenLength = 4_096;
    private int maxPayloadBytes = 1_024;

    @Override
    public void afterPropertiesSet() {
        validateSecret(secret, "NEWSLETTER_UNSUBSCRIBE_TOKEN_SECRET");
        validateKeyId(keyId);
        if (formatVersion != 1) {
            throw new IllegalStateException("newsletter unsubscribe token formatVersion은 1이어야 합니다.");
        }
        if (maxFutureSkewSeconds < 0) {
            throw new IllegalStateException("token future skew는 0 이상이어야 합니다.");
        }
        if (maxTokenLength < 64 || maxPayloadBytes < 64) {
            throw new IllegalStateException("token 크기 제한이 너무 작습니다.");
        }
        if (previousKeys == null) {
            throw new IllegalStateException("previous key 설정은 null일 수 없습니다.");
        }

        List<byte[]> seenSecrets = new ArrayList<>();
        seenSecrets.add(activeSecretBytes());
        for (Map.Entry<String, String> entry : previousKeys.entrySet()) {
            validateKeyId(entry.getKey());
            if (keyId.equals(entry.getKey())) {
                throw new IllegalStateException("active keyId는 previous keyId와 달라야 합니다.");
            }
            validateSecret(entry.getValue(), "previous unsubscribe token secret");
            byte[] candidate = entry.getValue().getBytes(StandardCharsets.UTF_8);
            if (seenSecrets.stream().anyMatch(existing ->
                    java.security.MessageDigest.isEqual(existing, candidate))) {
                throw new IllegalStateException("unsubscribe token key마다 다른 secret이 필요합니다.");
            }
            seenSecrets.add(candidate);
        }
    }

    public byte[] activeSecretBytes() {
        return secret == null
                ? new byte[0]
                : secret.getBytes(StandardCharsets.UTF_8);
    }

    public Map<String, byte[]> verificationSecrets() {
        Map<String, byte[]> keys = new LinkedHashMap<>();
        keys.put(keyId, activeSecretBytes());
        previousKeys.forEach((previousKeyId, previousSecret) -> keys.put(
                previousKeyId,
                previousSecret.getBytes(StandardCharsets.UTF_8)));
        return keys;
    }

    public List<byte[]> allSecretBytes() {
        return verificationSecrets().values().stream()
                .map(byte[]::clone)
                .toList();
    }

    private void validateSecret(String candidate, String name) {
        if (candidate == null
                || candidate.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(name + "은 UTF-8 기준 32 byte 이상이어야 합니다.");
        }
    }

    private void validateKeyId(String candidate) {
        if (candidate == null || !KEY_ID_PATTERN.matcher(candidate).matches()) {
            throw new IllegalStateException("unsubscribe token keyId 형식이 올바르지 않습니다.");
        }
    }
}
