package web.tosunsaeng.domain.newsletter.domain.policy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import web.tosunsaeng.domain.newsletter.config.NewsletterUnsubscribeTokenProperties;
import web.tosunsaeng.domain.newsletter.exception.NewsletterException;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class NewsletterUnsubscribeTokenManager {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Pattern BASE64_URL_SEGMENT =
            Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "formatVersion",
            "keyId",
            "subscriberId",
            "subscriberTokenVersion",
            "issuedAtEpochSeconds");
    private static final int HMAC_BYTES = 32;
    private static final int MAX_SUBSCRIBER_ID_LENGTH = 256;

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final NewsletterUnsubscribeTokenProperties properties;
    private final Map<String, byte[]> verificationSecrets;

    public NewsletterUnsubscribeTokenManager(
            ObjectMapper objectMapper,
            Clock clock,
            NewsletterUnsubscribeTokenProperties properties) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
        this.verificationSecrets = properties.verificationSecrets();
    }

    public String createToken(String subscriberId, long subscriberTokenVersion) {
        validateSubscriberClaims(subscriberId, subscriberTokenVersion);
        TokenPayload payload = new TokenPayload(
                properties.getFormatVersion(),
                properties.getKeyId(),
                subscriberId,
                subscriberTokenVersion,
                clock.instant().getEpochSecond());
        byte[] payloadBytes;
        try {
            payloadBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("구독 해지 token payload를 생성할 수 없습니다.");
        }
        if (payloadBytes.length > properties.getMaxPayloadBytes()) {
            throw new IllegalStateException("구독 해지 token payload가 허용 크기를 초과했습니다.");
        }
        String encodedPayload = encode(payloadBytes);
        String encodedSignature = encode(sign(
                encodedPayload,
                properties.activeSecretBytes()));
        String token = encodedPayload + "." + encodedSignature;
        if (token.length() > properties.getMaxTokenLength()) {
            throw new IllegalStateException("구독 해지 token이 허용 크기를 초과했습니다.");
        }
        return token;
    }

    public TokenClaims verify(String token) {
        if (token == null
                || token.isBlank()
                || !token.equals(token.strip())
                || token.length() > properties.getMaxTokenLength()) {
            throw invalidToken();
        }
        String[] segments = token.split("\\.", -1);
        if (segments.length != 2
                || !isCanonicalSegment(segments[0])
                || !isCanonicalSegment(segments[1])) {
            throw invalidToken();
        }

        byte[] payloadBytes = decode(segments[0]);
        byte[] providedSignature = decode(segments[1]);
        if (payloadBytes.length > properties.getMaxPayloadBytes()
                || providedSignature.length != HMAC_BYTES) {
            throw invalidToken();
        }
        JsonNode payload = readPayload(payloadBytes);
        if (!payload.isObject()
                || payload.size() != PAYLOAD_FIELDS.size()
                || !PAYLOAD_FIELDS.equals(fieldNames(payload))) {
            throw invalidToken();
        }

        int formatVersion = requiredInt(payload, "formatVersion");
        String keyId = requiredText(payload, "keyId");
        String subscriberId = requiredText(payload, "subscriberId");
        long subscriberTokenVersion = requiredLong(payload, "subscriberTokenVersion");
        long issuedAtEpochSeconds = requiredLong(payload, "issuedAtEpochSeconds");
        byte[] secret = verificationSecrets.get(keyId);
        if (secret == null
                || formatVersion != properties.getFormatVersion()
                || !MessageDigest.isEqual(
                        sign(segments[0], secret),
                        providedSignature)) {
            throw invalidToken();
        }

        validateSubscriberClaims(subscriberId, subscriberTokenVersion);
        Instant issuedAt;
        try {
            issuedAt = Instant.ofEpochSecond(issuedAtEpochSeconds);
        } catch (DateTimeException exception) {
            throw invalidToken();
        }
        if (issuedAtEpochSeconds < 0
                || issuedAt.isAfter(clock.instant().plusSeconds(
                        properties.getMaxFutureSkewSeconds()))) {
            throw invalidToken();
        }
        return new TokenClaims(
                formatVersion,
                keyId,
                subscriberId,
                subscriberTokenVersion,
                issuedAt);
    }

    private JsonNode readPayload(byte[] payloadBytes) {
        try {
            return objectMapper.readTree(payloadBytes);
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private Set<String> fieldNames(JsonNode payload) {
        Set<String> names = new java.util.HashSet<>();
        payload.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private int requiredInt(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalidToken();
        }
        return value.intValue();
    }

    private long requiredLong(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
            throw invalidToken();
        }
        return value.longValue();
    }

    private String requiredText(JsonNode payload, String name) {
        JsonNode value = payload.get(name);
        if (value == null || !value.isTextual()) {
            throw invalidToken();
        }
        return value.textValue();
    }

    private void validateSubscriberClaims(String subscriberId, long tokenVersion) {
        if (subscriberId == null
                || subscriberId.isBlank()
                || subscriberId.length() > MAX_SUBSCRIBER_ID_LENGTH
                || tokenVersion < 1) {
            throw invalidToken();
        }
    }

    private boolean isCanonicalSegment(String segment) {
        if (segment.isEmpty() || !BASE64_URL_SEGMENT.matcher(segment).matches()) {
            return false;
        }
        try {
            return encode(Base64.getUrlDecoder().decode(segment)).equals(segment);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] decode(String segment) {
        try {
            return Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException exception) {
            throw invalidToken();
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] sign(String encodedPayload, byte[] secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(encodedPayload.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("구독 해지 token 서명을 처리할 수 없습니다.");
        }
    }

    private NewsletterException invalidToken() {
        return new NewsletterException(ErrorStatus._NEWSLETTER_UNSUBSCRIBE_TOKEN_INVALID);
    }

    @JsonPropertyOrder({
            "formatVersion",
            "keyId",
            "subscriberId",
            "subscriberTokenVersion",
            "issuedAtEpochSeconds"
    })
    private record TokenPayload(
            int formatVersion,
            String keyId,
            String subscriberId,
            long subscriberTokenVersion,
            long issuedAtEpochSeconds) {
    }

    public record TokenClaims(
            int formatVersion,
            String keyId,
            String subscriberId,
            long subscriberTokenVersion,
            Instant issuedAt) {
    }
}
